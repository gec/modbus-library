/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.modbus.process

import java.io.IOException
import java.nio.ByteBuffer

import com.typesafe.scalalogging.slf4j.Logging
import org.totalgrid.modbus.data.UInt16
import org.totalgrid.modbus.io.{ ChannelUser, ClientConnection, ConnectionSource }
import org.totalgrid.modbus.parse._
import org.totalgrid.modbus.pdu._
import org.totalgrid.modbus.poll._
import org.totalgrid.modbus._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Future, Promise, future, promise }

object ModbusMasterProcess {

  sealed trait MasterState {
    def name = this.getClass.getSimpleName
  }
  case object NotConnected extends MasterState
  case class Idle(connection: ClientConnection) extends MasterState
  case class ResponsePending(connection: ClientConnection, handler: ResponseHandler) extends MasterState
  case object Closed extends MasterState

  sealed trait Operation
  case class WriteRequestOperation(wr: WriteRequest, prom: Promise[Boolean]) extends Operation
  case class ReadRequestOperation[A](rr: RequestPdu, parser: PduParser[A], handler: A => Unit, prom: Promise[A]) extends Operation
}

class ModbusMasterProcess(
    scheduler: Scheduler,
    dispatcher: Dispatcher,
    source: ConnectionSource,
    host: String,
    port: Int,
    connectTimeoutMs: Long,
    connectRetryIntervalMs: Long,
    operationTimeoutMs: Long,
    deviceObs: ModbusDeviceObserver,
    channelObs: ChannelObserver,
    polls: Seq[Poll],
    aduParser: AduParser) extends ModbusMaster with Logging {
  import ModbusMasterProcess._

  private val mutex = new Object
  private var state: MasterState = NotConnected
  private var requestSequence: Int = 0
  private val taskManager = new TaskManager(polls)
  private val localReadBuffer = ByteBuffer.allocate(512)
  private val writeBuffer = ByteBuffer.allocate(512)
  private val operationQueue = new scala.collection.mutable.Queue[Operation]

  private val id = s"Master[$host:$port]"

  def start(): Unit = {
    mutex.synchronized {
      doConnectAttempt()
    }
  }

  def readDiscreteInputs(start: Int, count: Int): Future[Seq[ModbusBit]] = {
    val pdu = new ReadDiscreteInputRequest(UInt16(start), UInt16(count))
    val code = FunctionCode.READ_DISCRETE_INPUTS
    val parser = new BitResponseParser(code.code, code.error, start, count)
    val handler = (v: Seq[ModbusBit]) => deviceObs.onReadDiscreteInput(v)

    handleReadOperation(pdu, parser, handler)
  }
  def readCoilStatuses(start: Int, count: Int): Future[Seq[ModbusBit]] = {
    val pdu = new ReadCoilsRequest(UInt16(start), UInt16(count))
    val code = FunctionCode.READ_COILS
    val parser = new BitResponseParser(code.code, code.error, start, count)
    val handler = (v: Seq[ModbusBit]) => deviceObs.onReadCoilStatus(v)

    handleReadOperation(pdu, parser, handler)
  }
  def readInputRegisters(start: Int, count: Int): Future[Seq[ModbusRegister]] = {
    val pdu = new ReadInputRegisterRequest(UInt16(start), UInt16(count))
    val code = FunctionCode.READ_INPUT_REGISTERS
    val parser = new RegisterResponseParser(code.code, code.error, start, count)
    val handler = (v: Seq[ModbusRegister]) => deviceObs.onReadInputRegister(v)

    handleReadOperation(pdu, parser, handler)
  }
  def readHoldingRegisters(start: Int, count: Int): Future[Seq[ModbusRegister]] = {
    val pdu = new ReadHoldingRegisterRequest(UInt16(start), UInt16(count))
    val code = FunctionCode.READ_HOLDING_REGISTERS
    val parser = new RegisterResponseParser(code.code, code.error, start, count)
    val handler = (v: Seq[ModbusRegister]) => deviceObs.onReadHoldingRegister(v)

    handleReadOperation(pdu, parser, handler)
  }

  private def handleReadOperation[A](pdu: RequestPdu, parser: PduParser[A], onResponse: A => Unit): Future[A] = {
    mutex.synchronized {
      state match {
        case NotConnected => Future.failed(new IllegalStateException("Not connected"))
        case Closed => Future.failed(new IOException("Connection closed"))
        case Idle(conn) => {
          val prom = promise[A]
          val handler = doReadRequest(conn, pdu, parser, onResponse, prom)
          state = ResponsePending(conn, handler)
          prom.future
        }
        case ResponsePending(conn, handler) => {
          val prom = promise[A]
          operationQueue.enqueue(ReadRequestOperation(pdu, parser, onResponse, prom))
          prom.future
        }
      }
    }
  }

  private def doReadRequest[A](conn: ClientConnection, pdu: RequestPdu, parser: PduParser[A], handler: A => Unit, prom: Promise[A]): ResponseHandler = {
    logger.debug(s"$id user-issued read request operation: ${pdu.getClass.getSimpleName}")
    doRequest(conn, pdu)
    scheduleTimeoutCheck(requestSequence, operationTimeoutMs)
    new ReadResponseHandler(id, localReadBuffer, requestSequence, dispatcher, aduParser, parser, handler, Some(prom))
  }

  def writeSingleCoil(index: Int, value: Boolean): Future[Boolean] = {
    commonWriteRequest(new WriteSingleCoilRequest(value, UInt16(index)))
  }
  def writeSingleRegister(index: Int, value: Int): Future[Boolean] = {
    val v: Int = if (value < 0) {
      val asShort = value.toShort
      (asShort & 0xFF) | (asShort & 0xFF00)
    } else {
      value
    }
    commonWriteRequest(new WriteSingleRegisterRequest(UInt16(v), UInt16(index)))
  }

  private def commonWriteRequest(wr: => WriteRequest): Future[Boolean] = {
    mutex.synchronized {
      state match {
        case NotConnected => Future.failed(new IllegalStateException("Not connected"))
        case Closed => Future.failed(new IOException("Connection closed"))
        case Idle(conn) => {
          val prom = promise[Boolean]
          val request = wr
          val handler = doWriteRequest(conn, request, prom)
          state = ResponsePending(conn, handler)
          prom.future
        }
        case ResponsePending(conn, handler) => {
          val prom = promise[Boolean]
          val request = wr
          operationQueue.enqueue(WriteRequestOperation(request, prom))
          prom.future
        }
      }
    }
  }

  private def doWriteRequest(conn: ClientConnection, request: WriteRequest, prom: Promise[Boolean]): ResponseHandler = {
    logger.debug(s"$id user-issued write request operation: ${request.getClass.getSimpleName}")
    doRequest(conn, request)
    scheduleTimeoutCheck(requestSequence, operationTimeoutMs)
    new WriteResponseHandler(id, localReadBuffer, requestSequence, aduParser, request.parser(), prom)
  }

  private def onConnectionSuccess(connection: ClientConnection): Unit = {
    mutex.synchronized {
      state match {
        case NotConnected => {
          logger.info(s"$id connection success")
          checkOperationsThenTasks(connection) match {
            case None => state = Idle(connection)
            case Some(respHandler) => state = ResponsePending(connection, respHandler)
          }
          notifyChannelOnline()
        }
        case Closed => {
          logger.debug(s"$id received connection success when closed")
          connection.close()
        }
        case st => logger.error(s"$id Received connection success in state $st")
      }
    }
  }

  private def onConnectionFailure(ex: Throwable): Unit = {
    mutex.synchronized {
      state match {
        case NotConnected => {
          logger.info(s"$id connection failure: " + ex.getMessage)
          scheduleConnectionRetry()
          notifyChannelOffline()
        }
        case Closed => logger.debug(s"$id received connection failure when closed: " + ex.getMessage)
        case st => logger.error(s"$id received connection failure in state $st" + ex.getMessage)
      }
    }
  }

  private def onConnectRetry(): Unit = {
    mutex.synchronized {
      state match {
        case NotConnected => {
          logger.info(s"$id retrying connection...")
          doConnectAttempt()
        }
        case Closed => logger.debug(s"$id received connection retry when closed")
        case st => logger.error(s"$id received connection retry in state $st")
      }
    }
  }

  private def onTimeoutCheck(sequenceOfRequest: Int): Unit = {
    mutex.synchronized {
      state match {
        case ResponsePending(conn, handler) =>
          if (requestSequence == sequenceOfRequest) {
            logger.warn(s"$id had timeout on request")

            scheduleConnectionRetry()

            requestSequence = (requestSequence + 1) % 65536
            state = NotConnected
            conn.close()
            notifyChannelOffline()
          }
        case _ =>
      }
    }
  }

  private def scheduleTimeoutCheck(sequenceOfRequest: Int, timeOffsetMs: Long): Unit = {
    scheduler.scheduleCall(() => onTimeoutCheck(sequenceOfRequest), timeOffsetMs)
  }

  private def onPollTimer(): Unit = {
    mutex.synchronized {
      state match {
        case NotConnected =>
        case Closed =>
        case ResponsePending(conn, handler) =>
        case Idle(conn) =>
          logger.trace(s"$id poll timer checking operations and tasks")
          checkOperationsThenTasks(conn) match {
            case None => state = Idle(conn)
            case Some(respHandler) => state = ResponsePending(conn, respHandler)
          }
      }
    }
  }

  private def onSendFailure(ex: Throwable): Unit = {

    def handleAndReconnect(): Unit = {
      logger.warn(s"$id had error on send: " + ex.getMessage)
      logger.debug(s"$id had error on send: " + ex)

      scheduleConnectionRetry()
      requestSequence = (requestSequence + 1) % 65536

      state = NotConnected
      notifyChannelOffline()
    }

    mutex.synchronized {
      state match {
        case NotConnected => logger.debug(s"$id had error on send while not connected: " + ex.getMessage)
        case Closed => logger.debug(s"$id had error on send while closed: " + ex.getMessage)
        case ResponsePending(conn, handler) => handleAndReconnect()
        case Idle(conn) => handleAndReconnect()
      }
    }
  }

  private def onReadData(buffer: ByteBuffer): Unit = {
    mutex.synchronized {
      state match {
        case NotConnected => logger.warn(s"$id got unexpected read data while not connected")
        case Closed => logger.warn(s"$id got unexpected read data after user closed")
        case Idle(conn) => logger.warn(s"$id got unexpected read data while idle")
        case ResponsePending(conn, handler) => {
          logger.trace(s"$id on read data: " + buffer.remaining())
          val completed = handler.handleData(buffer)
          if (completed) {
            requestSequence = (requestSequence + 1) % 65536
            logger.trace(s"$id read complete")
            checkOperationsThenTasks(conn) match {
              case None => state = Idle(conn)
              case Some(nextRpsHandler) => state = ResponsePending(conn, nextRpsHandler)
            }
          }
        }
      }
    }
  }

  private def onConnectionClosed(): Unit = {

    def handleWhileConnected(conn: ClientConnection): Unit = {
      logger.info(s"$id closed by user")
      state = NotConnected
      notifyChannelOffline()
      scheduleConnectionRetry()
    }

    mutex.synchronized {
      state match {
        case NotConnected => logger.debug(s"$id got channel closed while not connected")
        case Closed => //logger.debug(s"$id got channel closed after user closed")
        case ResponsePending(conn, handler) => handleWhileConnected(conn)
        case Idle(conn) => handleWhileConnected(conn)
      }
    }
  }

  private def doConnectAttempt(): Unit = {
    val connectFuture = future {
      source.connect(host, port, connectTimeoutMs, channelCallbacks)
    }

    connectFuture.onSuccess { case c => onConnectionSuccess(c) }
    connectFuture.onFailure { case ex => onConnectionFailure(ex) }

    notifyChannelOpening()
  }

  private def doRequest(connection: ClientConnection, pdu: RequestPdu): Unit = {
    writeBuffer.clear()

    val sequence = requestSequence
    aduParser.writePdu(writeBuffer, pdu, sequence)
    writeBuffer.flip()

    val writeFut = future {
      connection.send(writeBuffer)
      sequence
    }
    writeFut.onSuccess { case _ => }
    writeFut.onFailure { case ex => onSendFailure(ex) }
  }

  private def scheduleConnectionRetry(): Unit = {
    scheduler.scheduleCall(onConnectRetry, connectRetryIntervalMs)
  }

  private def checkOperationsThenTasks(connection: ClientConnection): Option[ResponseHandler] = {
    if (operationQueue.nonEmpty) {
      val operation = operationQueue.dequeue()
      operation match {
        case WriteRequestOperation(wr, prom) =>
          Some(doWriteRequest(connection, wr, prom))
        case ReadRequestOperation(rr, parser, handler, prom) =>
          Some(doReadRequest(connection, rr, parser, handler, prom))
      }
    } else {
      checkTasks(connection)
    }
  }

  private def checkTasks(connection: ClientConnection): Option[ResponseHandler] = {
    if (polls.nonEmpty) {
      val timeUntilNext = taskManager.timeUntilNext()
      if (timeUntilNext > 0) {
        logger.debug(s"$id scheduling next poll $timeUntilNext in future")
        scheduler.scheduleCall(onPollTimer, timeUntilNext)
        None
      } else {

        val poll = taskManager.next()

        val (handler, timeoutMs) = poll match {
          case rd: ReadDiscreteInput =>
            logger.debug(s"$id issuing poll ReadDiscreteInput")
            (new ReadResponseHandler(id, localReadBuffer, requestSequence, dispatcher, aduParser, rd.resultHandler(), deviceObs.onReadDiscreteInput, None),
              poll.timeoutMs)
          case rd: ReadCoilStatus =>
            logger.debug(s"$id issuing poll ReadCoilStatus")
            (new ReadResponseHandler(id, localReadBuffer, requestSequence, dispatcher, aduParser, rd.resultHandler(), deviceObs.onReadCoilStatus, None),
              poll.timeoutMs)
          case rd: ReadInputRegister =>
            logger.debug(s"$id issuing poll ReadInputRegister")
            (new ReadResponseHandler(id, localReadBuffer, requestSequence, dispatcher, aduParser, rd.resultHandler(), deviceObs.onReadInputRegister, None),
              poll.timeoutMs)
          case rd: ReadHoldingRegister =>
            logger.debug(s"$id issuing poll ReadHoldingRegister")
            (new ReadResponseHandler(id, localReadBuffer, requestSequence, dispatcher, aduParser, rd.resultHandler(), deviceObs.onReadHoldingRegister, None),
              poll.timeoutMs)
        }

        doRequest(connection, poll.pdu())
        scheduleTimeoutCheck(requestSequence, timeoutMs)

        Some(handler)
      }
    } else {
      None
    }
  }

  private val channelCallbacks = new ChannelUser {
    def onData(buffer: ByteBuffer): Unit = {
      onReadData(buffer)
    }

    def onClosed(): Unit = {
      onConnectionClosed()
    }
  }

  def close(): Unit = {

    def handleWhileConnected(conn: ClientConnection): Unit = {
      logger.info(s"$id closed by user")
      state = Closed
      conn.close()
      notifyChannelOffline()
    }

    mutex.synchronized {
      state match {
        case NotConnected => {
          logger.info(s"$id closed by user")
          state = Closed
        }
        case Closed => logger.debug(s"$id closed while already closed")
        case ResponsePending(conn, handler) => handleWhileConnected(conn)
        case Idle(conn) => handleWhileConnected(conn)
      }
    }
  }

  private def notifyChannelOnline(): Unit = {
    try {
      channelObs.onChannelOnline()
    } catch {
      case ex: Throwable =>
        logger.warn("Caught exception from channel callback: " + ex.getMessage)
    }
  }
  private def notifyChannelOffline(): Unit = {
    try {
      channelObs.onChannelOffline()
    } catch {
      case ex: Throwable =>
        logger.warn("Caught exception from channel callback: " + ex.getMessage)
    }
  }
  private def notifyChannelOpening(): Unit = {
    try {
      channelObs.onChannelOpening()
    } catch {
      case ex: Throwable =>
        logger.warn("Caught exception from channel callback: " + ex.getMessage)
    }
  }

}


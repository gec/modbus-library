package org.totalgrid.modbus.impl

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
import net.agileautomata.nio4s._

import net.agileautomata.executor4s._
import java.nio.ByteBuffer
import com.typesafe.scalalogging.slf4j.Logging

import org.totalgrid.modbus._

private[modbus] trait AduExecutor {
  def execute(adu: AduHandler): Future[RequestResponse]
}

private[modbus] trait AduExecutorListener {
  def online(exe: AduExecutor): Unit
  def offline(): Unit
}

private[modbus] case object NullAduExecutorListener extends AduExecutorListener {
  def online(exe: AduExecutor): Unit = {}
  def offline(): Unit = {}
}

sealed trait RequestResponse
final case object SuccessResponse extends RequestResponse
final case object ChannelClosedResponse extends RequestResponse
final case class ExceptionResponse(ex: Exception) extends RequestResponse

/**
 * Handles requests sequentially. Multi-dropped slave polling would share a common instance of this class.
 */
private[modbus] final class RequestExecutor(factory: ChannelFactory, config: ConnectionManager.Config)(listener: AduExecutorListener) extends ChannelListener with AduExecutor with Logging {

  // mutable state field
  private var state = ChannelState()

  def shutdown() = {
    factory.strand.terminate(state.channel.foreach(_.close()))
  }

  private val manager = new ConnectionManager(factory, this, config)

  def execute(adu: AduHandler): Future[RequestResponse] = {

    val future = factory.strand.future[RequestResponse]

    def inner = {

      val callback = new RequestCallback {

        def onSuccess(): Unit = {
          future.set(SuccessResponse)
          next(_.complete(true).startDelayed)
        }

        def onTimeout(): Unit = {
          future.set(ExceptionResponse(new ResponseTimeoutException(adu.getTimeout)))
          next(_.complete(false).startDelayed)
        }

        def onFailure(ex: Exception): Unit = {
          future.set(ExceptionResponse(ex))
          next(_.complete(true).startDelayed)
        }

        def onChannelClosed(): Unit = {
          future.set(ChannelClosedResponse)
          next(_.startDelayed)
        }

      }
      next(_.enqueue(adu, callback).startDelayed)
      future
    }

    factory.strand.execute(inner)
    future
  }

  def next(f: ChannelState => ChannelState): ChannelState = {
    val s = f(state)
    state = s
    s
  }

  def onData(buffer: ByteBuffer): Unit = {
    logger.debug("Received: " + toHex(buffer))
    state.active match {
      case None =>
        logger.warn("Discarding bytes received outside of a request: " + buffer.remaining())
        buffer.clear()
      case Some(x) =>
        try {
          handleData(x.factory, x.callback, buffer)
        } catch {
          case ex: Exception =>
            logger.error("Exception parsing response", ex)
            buffer.clear()
        }
    }
  }

  //TODO - move the parser to the type definitions since it's pure functional
  private def handleData(factory: AduHandler, callback: RequestCallback, buffer: ByteBuffer) = {
    factory.handleData(buffer) match {
      case Preserve =>
        buffer.preserve()
      case Discard =>
        buffer.clear()
      case ValidResponse =>
        buffer.clear()
        callback.onSuccess()
      case ResponseException(ex: Byte) =>
        buffer.clear()
        callback.onFailure("Response NACK: " + ErrorCode(ex))
    }
  }

  override def onChannelOpening() = {}

  //implement ChannelListener
  override def onChannelOpen(channel: Channel) = {
    logger.info("Connection successful: " + factory)
    ChannelReader(channel, ByteBuffer.allocateDirect(512))(d => factory.strand.execute(onData(d)))
    next(_.onChannelOpen(channel))
    listener.online(this)
  }

  override def onException(ex: Exception) = {
    logger.error("Channel closed", ex)
    next(_.onChannelClose)
    listener.offline()
  }

  override def onConnectionFailure(ex: Exception, waitMs: Long) = {
    logger.error("Error connecting, waiting " + waitMs + " ms to retry")
  }
}
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
package org.totalgrid.modbus.japi.impl

import org.totalgrid.modbus._
import org.totalgrid.modbus.japi.ChannelObserver
import org.totalgrid.modbus.japi._
import org.totalgrid.modbus.data.UInt16
import org.totalgrid.modbus.pdu.{ WriteSingleRegisterRequest, WriteSingleCoilRequest }
import org.totalgrid.modbus.poll._
import scala.collection.JavaConversions._
import scala.concurrent.Future

object Conversions {

  implicit def convertBytePair(v: ByteX2): BytePair = {
    new BytePair(v.first, v.second)
  }

  def convertPoll(j: ModbusPoll): Poll = {
    j.getType match {
      case PollType.DiscreteInput =>
        ReadDiscreteInput(UInt16(j.getStart), UInt16(j.getCount), j.getIntervalMs, j.getTimeoutMs)
      case PollType.CoilStatus =>
        ReadCoilStatus(UInt16(j.getStart), UInt16(j.getCount), j.getIntervalMs, j.getTimeoutMs)
      case PollType.InputRegister =>
        ReadInputRegister(UInt16(j.getStart), UInt16(j.getCount), j.getIntervalMs, j.getTimeoutMs)
      case PollType.HoldingRegister =>
        ReadHoldingRegister(UInt16(j.getStart), UInt16(j.getCount), j.getIntervalMs, j.getTimeoutMs)
    }
  }

  def convertPollList(j: java.util.List[ModbusPoll]): Seq[Poll] = {
    j.map(convertPoll).toList
  }

}

import Conversions._

class DataObsShim(j: ModbusDataObserver, jcomm: CommsObserver) extends ModbusDeviceObserver {
  override def onReadDiscreteInput(list: Traversable[ModbusBit]): Unit = {
    val sseq = list.toVector.map { s => new ModbusBitValue(s.index, s.value) }
    j.onReadDiscreteInput(sseq)
  }

  override def onReadCoilStatus(list: Traversable[ModbusBit]): Unit = {
    val sseq = list.toVector.map { s => new ModbusBitValue(s.index, s.value) }
    j.onReadCoilStatus(sseq)
  }

  override def onReadHoldingRegister(list: Traversable[ModbusRegister]): Unit = {
    val sseq = list.toVector.map { s => new ModbusRegisterValue(s.index, s.value) }
    j.onReadHoldingRegister(sseq)
  }

  override def onReadInputRegister(list: Traversable[ModbusRegister]): Unit = {
    val sseq = list.toVector.map { s => new ModbusRegisterValue(s.index, s.value) }
    j.onReadInputRegister(sseq)
  }

  override def onCommSuccess(): Unit = {
    jcomm.onCommSuccess()
  }

  override def onCommFailure(): Unit = {
    jcomm.onCommFailure()
  }
}

class ChannelObsShim(j: ChannelObserver) extends org.totalgrid.modbus.ChannelObserver {
  override def onChannelOpening(): Unit = j.onChannelOpening()

  override def onChannelOnline(): Unit = j.onChannelOnline()

  override def onChannelOffline(): Unit = j.onChannelOffline()
}

object CommandHandlerShim {
  import scala.concurrent.ExecutionContext.Implicits.global
  def handleFuture(fut: Future[Boolean], resultHandler: CommandResultHandler): Unit = {
    fut.onFailure { case ex => resultHandler.completed(false) }
    fut.onSuccess { case v => resultHandler.completed(true) }
  }
}
class CommandHandlerShim(s: ModbusOperations) extends ModbusCommandHandler {

  def writeSingleCoil(index: Int, value: Boolean, resultHandler: CommandResultHandler): Unit = {
    CommandHandlerShim.handleFuture(s.writeSingleCoil(index, value), resultHandler)
  }

  def writeSingleRegister(index: Int, value: Int, resultHandler: CommandResultHandler): Unit = {
    CommandHandlerShim.handleFuture(s.writeSingleRegister(index, value), resultHandler)
  }

}
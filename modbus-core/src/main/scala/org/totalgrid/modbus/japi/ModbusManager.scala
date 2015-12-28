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
package org.totalgrid.modbus.japi

import org.totalgrid.modbus.japi.impl.{ CommandHandlerShim, Conversions, ChannelObsShim, DataObsShim }
import org.totalgrid.modbus.{ ModbusManager => ScalaModbusManager }

class ModbusManager {

  private val m = ScalaModbusManager.start(8192, 6)

  def addTcpMaster(host: String,
    port: Int,
    modbusAddress: Byte,
    dataObserver: ModbusDataObserver,
    commsObserver: CommsObserver,
    channelObserver: ChannelObserver,
    polls: java.util.List[ModbusPoll]): MasterHandle = {

    val mdo = new DataObsShim(dataObserver, commsObserver)
    val co = new ChannelObsShim(channelObserver)
    val spolls = Conversions.convertPollList(polls)

    val master = m.addTcpMaster(host, port, modbusAddress, mdo, co, spolls, 10000, 5000, 5000)

    new MasterHandle {
      def cancel(): Unit = master.close()

      def getCommandHandler: ModbusCommandHandler = new CommandHandlerShim(master)
    }
  }

  def addRtuMaster(host: String,
    port: Int,
    modbusAddress: Byte,
    dataObserver: ModbusDataObserver,
    commsObserver: CommsObserver,
    channelObserver: ChannelObserver,
    polls: java.util.List[ModbusPoll]): MasterHandle = {

    val mdo = new DataObsShim(dataObserver, commsObserver)
    val co = new ChannelObsShim(channelObserver)
    val spolls = Conversions.convertPollList(polls)

    val master = m.addTcpMaster(host, port, modbusAddress, mdo, co, spolls, 10000, 5000, 5000)

    new MasterHandle {
      def cancel(): Unit = master.close()

      def getCommandHandler: ModbusCommandHandler = new CommandHandlerShim(master)
    }
  }

  def shutdown(): Unit = {
    m.shutdown()
  }
}

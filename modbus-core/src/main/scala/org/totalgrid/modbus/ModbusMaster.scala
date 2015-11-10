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
package org.totalgrid.modbus

import org.totalgrid.modbus.impl._
import net.agileautomata.nio4s.IoService
import net.agileautomata.executor4s.{ Executor, Cancelable }

// TODO - make all of the parameters externally configurable
class ModbusMaster {

  case class MasterRecord(handler: CommandHandler, cancel: Cancelable)

  private val service = IoService()

  private class Listener(
      channel: ChannelObserver,
      device: ModbusDeviceObserver,
      strand: Executor,
      polls: List[Poll],
      factory: AduHandlerFactory,
      seqGen: SequenceManager) extends AduExecutorListener {

    def offline() = channel.onChannelOffline()
    def online(exe: AduExecutor) {
      channel.onChannelOnline()
      new PollManager(exe, device, 3, strand, polls, factory, seqGen)
    }
  }

  def addTcpClient(
    host: String,
    port: Int,
    deviceObs: ModbusDeviceObserver,
    channelObs: ChannelObserver,
    polls: List[Poll],
    factory: AduHandlerFactory): MasterRecord = {

    val cf = ChannelFactory.tcpClient(service, host, port)
    val seqMgr = SequenceManager()
    val listener = new Listener(channelObs, deviceObs, cf.strand, polls, factory, seqMgr) with LastValueFiltering
    val mgr = new RequestExecutor(cf, ConnectionManager.Config(1000, 10000))(listener)
    val handler = new CommandHandlerImpl(mgr, factory, 1000, seqMgr) // TODO make this command timeout configurable

    val cancelable = new Cancelable {
      def cancel() = {
        mgr.shutdown()
        deviceObs.onCommFailure()
        channelObs.onChannelOffline()
      }
    }
    MasterRecord(handler, cancelable)
  }

  def shutdown() {
    service.shutdown()
  }

}
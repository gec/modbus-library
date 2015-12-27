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

import java.util.concurrent.Executors

import org.totalgrid.modbus.io.NioService
import org.totalgrid.modbus.parse.{ RtuAduParser, TcpAduParser }
import org.totalgrid.modbus.poll.Poll
import org.totalgrid.modbus.process.{ Dispatcher, Scheduler, ModbusMasterProcess }

object ModbusManager {
  def start(readBufferSize: Int, scheduledPool: Int): ModbusManager = {
    val nio = new NioService(readBufferSize)
    val thread = new Thread(new Runnable {
      override def run(): Unit = {
        nio.run()
      }
    }, "Modbus IO")

    thread.start()
    new ModbusManager(nio, thread, scheduledPool)
  }
}
class ModbusManager(nio: NioService, nioThread: Thread, scheduledPool: Int) {
  private val mutex = new Object
  private val scheduleService = Executors.newScheduledThreadPool(scheduledPool)
  private val dispatchService = Executors.newCachedThreadPool()

  def addTcpMaster(
    host: String,
    port: Int,
    unitIdentifier: Byte,
    deviceObs: ModbusDeviceObserver,
    channelObs: ChannelObserver,
    polls: Seq[Poll],
    connectionTimeoutMs: Long,
    connectionRetryMs: Long): ModbusMaster = {

    val scheduler = Scheduler.build(scheduleService)
    val dispatcher = Dispatcher.build(dispatchService)
    val aduParser = new TcpAduParser(unitIdentifier)

    val modbusProcess = new ModbusMasterProcess(
      scheduler,
      dispatcher,
      nio,
      host,
      port,
      connectionTimeoutMs,
      connectionRetryMs,
      deviceObs,
      channelObs,
      polls,
      aduParser)

    modbusProcess.start()
    modbusProcess
  }

  def addRtuMaster(host: String,
    port: Int,
    deviceAddress: Byte,
    deviceObs: ModbusDeviceObserver,
    channelObs: ChannelObserver,
    polls: Seq[Poll],
    connectionTimeoutMs: Long,
    connectionRetryMs: Long): ModbusMaster = {

    val scheduler = Scheduler.build(scheduleService)
    val dispatcher = Dispatcher.build(dispatchService)
    val aduParser = new RtuAduParser(deviceAddress)

    val modbusProcess = new ModbusMasterProcess(
      scheduler,
      dispatcher,
      nio,
      host,
      port,
      connectionTimeoutMs,
      connectionRetryMs,
      deviceObs,
      channelObs,
      polls,
      aduParser)

    modbusProcess.start()
    modbusProcess
  }

  def shutdown(): Unit = {
    mutex.synchronized {
      scheduleService.shutdown()
      dispatchService.shutdown()
      nio.close()
      nioThread.join()
    }
  }

}


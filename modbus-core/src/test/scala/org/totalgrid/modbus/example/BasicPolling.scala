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
package org.totalgrid.modbus.example

import net.agileautomata.nio4s.IoService
import org.totalgrid.modbus.impl._
import com.typesafe.scalalogging.slf4j.Logging
import org.totalgrid.modbus._

import net.agileautomata.executor4s.StrandLifeCycle

object BasicPolling extends App with Logging {

  if (args.size != 3) {
    println("usage: <ip address> <port> <modbus address>")
  } else {
    val host = args(0)
    val port = args(1).toInt
    val address = args(2).toByte

    IoService.run { io =>
      val factory = ChannelFactory.tcpClient(io, host, port)
      val seqGen = SequenceManager()
      val listener = new Listener(factory.strand, address, seqGen) with LastValueFiltering
      val mgr = new RequestExecutor(factory, ConnectionManager.Config(1000, 60000))(listener)
      val handler = new CommandHandlerImpl(mgr, AduHandlerFactory.rtu(address), 1000, seqGen)

      Thread.sleep(1000)

      handler.issue(new WriteSingleRegisterRequest(UInt16(10), UInt16(0)))
      Thread.sleep(10)
      handler.issue(new WriteSingleCoilRequest(true, UInt16(1)))

      Console.readLine()
      mgr.shutdown()
    }
  }

  class Listener(strand: StrandLifeCycle, address: Byte, seqGen: SequenceManager) extends AduExecutorListener with ModbusDeviceObserver {

    def offline() = strand.terminate()

    def online(exe: AduExecutor) = {
      val poll = ReadHoldingRegister(UInt16(0), UInt16(10), 3000, 3000)
      new PollManager(exe, this, 2, strand, List(poll), AduHandlerFactory.rtu(address), seqGen)
    }

    override def onReadDiscreteInput(list: Traversable[ModbusBit]) = list.foreach(println)
    override def onReadHoldingRegister(list: Traversable[ModbusRegister]) = list.foreach(println)
    override def onCommSuccess() = println("comm success")
    override def onCommFailure() = println("comm failure")
  }

}
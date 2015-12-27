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
package org.totalgrid.modbus.integration

import java.net.InetAddress

import net.wimpi.modbus.ModbusCoupler
import net.wimpi.modbus.net.{ ModbusTCPListener, TCPConnectionHandler, TCPSlaveConnection }
import net.wimpi.modbus.procimg._
import net.wimpi.modbus.util.ThreadPool
import org.apache.commons.lang3.reflect.FieldUtils

class ModbusSlave(port: Int, unitId: Int) {

  def setDiscreteInput(index: Int, v: Boolean) {
    spi.setDigitalIn(index, new SimpleDigitalIn(v))
  }

  def setInputRegister(index: Int, v: Int) {
    spi.setInputRegister(index, new SimpleInputRegister(v))
  }

  def getDigitalOut(index: Int): Boolean = {
    spi.getDigitalOut(index).isSet
  }

  def getHoldingRegister(index: Int): Int = {
    spi.getRegister(index).getValue
  }
  def getHoldingRegisterSigned(index: Int): Short = {
    spi.getRegister(index).toShort
  }

  private val listener = new ModbusTCPListener(3)
  private val pool = new ThreadPoolWrap(3)
  FieldUtils.writeDeclaredField(listener, "m_ThreadPool", pool, true)

  private val spi = new SimpleProcessImage
  spi.addDigitalIn(new SimpleDigitalIn(false))
  spi.addDigitalIn(new SimpleDigitalIn(false))
  spi.addInputRegister(new SimpleInputRegister(0))
  spi.addInputRegister(new SimpleInputRegister(0))

  spi.addDigitalOut(new SimpleDigitalOut(false))
  spi.addDigitalOut(new SimpleDigitalOut(false))

  spi.addRegister(new SimpleInputRegister(0))
  spi.addRegister(new SimpleInputRegister(0))
  spi.addRegister(new SimpleInputRegister(0))

  ModbusCoupler.getReference.setProcessImage(spi)
  ModbusCoupler.getReference.setMaster(false)
  ModbusCoupler.getReference.setUnitID(unitId)

  listener.setAddress(InetAddress.getLoopbackAddress)
  listener.setPort(port)
  listener.start()

  class ThreadPoolWrap(count: Int) extends ThreadPool(count) {
    private val mutex = new Object
    private var tasks = Vector.empty[TCPConnectionHandler]

    override def execute(task: Runnable): Unit = {
      task match {
        case handler: TCPConnectionHandler =>
          mutex.synchronized {
            tasks ++= Vector(handler)
          }
        case _ =>
      }

      super.execute(task)
    }

    def getTasks: Seq[TCPConnectionHandler] = {
      mutex.synchronized(tasks)
    }
  }

  def close() {

    pool.getTasks.foreach { connHandler =>
      FieldUtils.readField(connHandler, "m_Connection", true).asInstanceOf[TCPSlaveConnection].close()
    }

    listener.stop()
  }
}

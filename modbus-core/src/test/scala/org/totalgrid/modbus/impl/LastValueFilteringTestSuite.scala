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
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.totalgrid.modbus.{ ModbusRegister, ModbusBit, ModbusDeviceObserver }

@RunWith(classOf[JUnitRunner])
class LastValueFilteringTestSuite extends FunSuite with ShouldMatchers {

  class MockObserver extends ModbusDeviceObserver {
    var diCount = 0
    var csCount = 0
    var hrCount = 0
    var irCount = 0

    var successCount = 0
    var failureCount = 0

    override def onReadDiscreteInput(list: Traversable[ModbusBit]): Unit = diCount += 1
    override def onReadCoilStatus(list: Traversable[ModbusBit]): Unit = csCount += 1
    override def onReadHoldingRegister(list: Traversable[ModbusRegister]): Unit = hrCount += 1
    override def onReadInputRegister(list: Traversable[ModbusRegister]): Unit = irCount += 1

    override def onCommSuccess() = successCount += 1
    override def onCommFailure() = failureCount += 1
  }

  test("Di values are filtered") {
    val mock = new MockObserver with LastValueFiltering

    mock.onReadDiscreteInput(List(ModbusBit(0, true)))
    mock.diCount should equal(1)
    mock.onReadDiscreteInput(List(ModbusBit(0, true)))
    mock.diCount should equal(1)
    mock.onReadDiscreteInput(List(ModbusBit(0, false)))
    mock.diCount should equal(2)
  }

  test("map is reset when comms fail") {
    val mock = new MockObserver with LastValueFiltering

    mock.onCommSuccess()
    mock.onReadDiscreteInput(List(ModbusBit(0, true)))
    mock.diCount should equal(1)
    mock.onCommFailure()
    mock.onReadDiscreteInput(List(ModbusBit(0, true)))
    mock.diCount should equal(2)
  }

}
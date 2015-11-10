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

import testing.MockAduExecutor

import org.totalgrid.modbus._
import net.agileautomata.executor4s._
import net.agileautomata.executor4s.testing.MockExecutor

@RunWith(classOf[JUnitRunner])
class PollManagerTestSuite extends FunSuite with ShouldMatchers {
  def fixture(test: (MockAduExecutor, MockExecutor, MockTimeSource) => Unit): Unit = {
    val mockAduExe = new MockAduExecutor
    val mockExe = new MockExecutor()
    val poll = ReadDiscreteInput(UInt16(0), UInt16(1), 2500, 1000)
    val ts = new MockTimeSource(0)
    val seqGen = SequenceManager()
    new PollManager(mockAduExe, new ModbusDeviceObserver {}, 2, mockExe, List(poll), AduHandlerFactory.rtu(0x03), seqGen, ts)
    test(mockAduExe, mockExe, ts)
  }

  test("Tries first poll on construction") {
    fixture { (adu, exe, ts) =>
      adu.set(SuccessResponse)
      adu.isIdle should equal(true)
    }
  }

  test("Polls on a schedule") {
    fixture { (adu, exe, ts) =>
      adu.set(SuccessResponse)
      ts.advance(2500)
      exe.tick(2500.milliseconds)
      adu.isIdle should equal(false)
    }
  }

}
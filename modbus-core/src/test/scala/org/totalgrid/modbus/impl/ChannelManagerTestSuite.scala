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

import org.totalgrid.modbus.ChannelFactory
import net.agileautomata.executor4s._
import testing.{ MockAduHandler, MockChannel }
import net.agileautomata.executor4s.testing.{ MockExecutor, MockFuture }

@RunWith(classOf[JUnitRunner])
class ChannelManagerTestSuite extends FunSuite with ShouldMatchers {

  class MockChannelFactory(allowOpen: Boolean, exe: StrandLifeCycle) extends ChannelFactory {
    val channel = new MockChannel
    def open = MockFuture.defined(if (allowOpen) Success(channel) else Failure("blocked"))
    def strand = exe
  }

  def fixture(connect: Boolean)(f: (RequestExecutor, MockExecutor, MockChannel) => Unit): Unit = {
    val exe = new MockExecutor()
    val factory = new MockChannelFactory(connect, Strand(exe))
    val manager = new RequestExecutor(factory, ConnectionManager.Config(1000, 3000))(NullAduExecutorListener)
    f(manager, exe, factory.channel)
  }

  test("Immediately rejects requests while channel is closed") {
    fixture(false) { (mgr, exe, ch) =>
      val f1 = mgr.execute(new MockAduHandler("0A 0B 0C", 1000))
      exe.runUntilIdle()
      f1.await should equal(ChannelClosedResponse)
    }
  }

  test("Immediately sends request while channel is open and no request is active") {
    fixture(true) { (mgr, exe, ch) =>
      val f1 = mgr.execute(new MockAduHandler("0A 0B 0C", 1000))
      exe.runUntilIdle()
      ch.writes should equal(List("0A 0B 0C"))
    }
  }

  test("Successfully processes requests") {
    fixture(true) { (mgr, exe, ch) =>
      val f1 = mgr.execute(new MockAduHandler("0A 0B 0C", 1000))
      exe.runUntilIdle()
      ch.respond("0D 0E 0F") {
        _.remaining() should be >= (3)
      }
      exe.runUntilIdle()
      f1.await should equal(SuccessResponse)
    }
  }

  test("Partial responses correctly preserve existing data in buffer") {
    fixture(true) { (mgr, exe, ch) =>
      val f1 = mgr.execute(new MockAduHandler("0A 0B 0C", 1000))
      exe.runUntilIdle()
      ch.respond("0D 0E")(_.remaining() should be >= (3))
      exe.runUntilIdle()
      ch.respond("0F")(_.remaining() should be >= (1))
      exe.runUntilIdle()
      f1.await should equal(SuccessResponse)
    }
  }

}
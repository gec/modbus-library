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

import java.nio.ByteBuffer
import requests.RtuAduHandler

@RunWith(classOf[JUnitRunner])
class RtuRequestFactoryTestSuite extends FunSuite with ShouldMatchers {

  def fixture(address: Byte, func: Byte = 0x0D, numRspBytes: Int = 3)(f: RtuAduHandler => Unit): Unit = {
    val r = new PduHandler {
      def handleResponse(array: Array[Byte]) = {}
      def numResponseBytes: Int = numRspBytes
      def getBytes: Array[Byte] = throw new Exception("Unimplemented")
      val function = new FunctionCode(func)
    }
    f(new RtuAduHandler(r, address, 1000))
  }

  test("< 4 bytes indicates that more data is required") {
    fixture(0x0F) { rf =>
      rf.handleData("01 02 03") should equal(Preserve)
    }
  }

  test("Wrong address causes a discard") {
    fixture(0x0F) { rf =>
      rf.handleData("0E 02 03 04 05") should equal(Discard)
    }
  }

  test("Response exception is correcly recognized") {
    fixture(0x01, 0x02) { rf =>
      rf.handleData("01 82 01 81 60") should equal(ResponseException(0x01))
    }
  }

  test("Bad crc in response exception causes a discard") {
    fixture(0x0F) { rf =>
      rf.handleData("0F 8D 02 52 FF") should equal(Discard)
    }
  }

  test("Valid response is correctly recognized") {
    fixture(0x01, 0x02, 0x02) { rf =>
      rf.handleData("01 02 01 00 A1 88") should equal(ValidResponse)
    }
  }

  test("Bad crc in ACK causes a discard") {
    fixture(0x01, 0x02, 0x02) { rf =>
      rf.handleData("01 02 01 00 A1 FF") should equal(Discard)
    }
  }

  test("Passes the underlying response the correct bytes") {
    fixture(0x01, 0x02, 0x02) { rf =>
      rf.handleData("01 02 01 00 A1 88") should equal(ValidResponse)
    }
  }

}
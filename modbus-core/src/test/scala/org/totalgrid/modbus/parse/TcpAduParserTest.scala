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
package org.totalgrid.modbus.parse

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import org.totalgrid.modbus.ModbusBit
import org.totalgrid.modbus.data.Hex
import org.totalgrid.modbus.pdu.{ BitResponseParser, FunctionCode }

@RunWith(classOf[JUnitRunner])
class TcpAduParserTest extends FunSuite with ShouldMatchers {

  test("Valid response") {
    val adu = new TcpAduParser(0x03)

    val pduParser = new BitResponseParser(FunctionCode.READ_DISCRETE_INPUTS.code, FunctionCode.READ_DISCRETE_INPUTS.error, 0, 1)

    val result = adu.handleData(Hex.bufferFromHex("00 05 00 00 00 04 03 02 01 00"), pduParser, 5)

    result should equal(ValidResponse(Seq(ModbusBit(0, false))))
  }

  test("Valid response exception") {
    val adu = new TcpAduParser(0x03)

    val pduParser = new BitResponseParser(FunctionCode.READ_DISCRETE_INPUTS.code, FunctionCode.READ_DISCRETE_INPUTS.error, 0, 1)

    val result = adu.handleData(Hex.bufferFromHex("00 05 00 00 00 02 03 82"), pduParser, 5)

    result should equal(ResponseException(0x82.toByte))
  }

  test("Wrong TCP seq discarded") {
    val adu = new TcpAduParser(0x03)

    val pduParser = new BitResponseParser(FunctionCode.READ_DISCRETE_INPUTS.code, FunctionCode.READ_DISCRETE_INPUTS.error, 0, 1)

    val result = adu.handleData(Hex.bufferFromHex("00 08 00 00 00 04 03 02 01 00"), pduParser, 5)

    result should equal(Discard)
  }

  test("Wrong TCP unit identifier discarded") {
    val adu = new TcpAduParser(0x03)

    val pduParser = new BitResponseParser(FunctionCode.READ_DISCRETE_INPUTS.code, FunctionCode.READ_DISCRETE_INPUTS.error, 0, 1)

    val result = adu.handleData(Hex.bufferFromHex("00 05 00 00 00 04 09 02 01 00"), pduParser, 5)

    result should equal(Discard)
  }

}
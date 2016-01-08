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
package org.totalgrid.modbus.pdu

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import org.totalgrid.modbus.data.Hex
import org.totalgrid.modbus.parse.ValidResponse
import org.totalgrid.modbus.{ ByteX2, ModbusBit, ModbusRegister }

@RunWith(classOf[JUnitRunner])
class ReadResponseTest extends FunSuite with ShouldMatchers {

  test("Bit response offset") {

    val pduParser = new BitResponseParser(FunctionCode.READ_DISCRETE_INPUTS.code, FunctionCode.READ_DISCRETE_INPUTS.error, 35, 2)

    val result = pduParser.handleData(Hex.bufferFromHex("01 02"))

    result should equal(ValidResponse(Seq(ModbusBit(35, false), ModbusBit(36, true))))
  }

  test("Register response offset") {

    val pduParser = new RegisterResponseParser(FunctionCode.READ_INPUT_REGISTERS.code, FunctionCode.READ_INPUT_REGISTERS.error, 33, 2)

    val result = pduParser.handleData(Hex.bufferFromHex("04 00 05 00 08"))

    result should equal(ValidResponse(Seq(ModbusRegister(33, ByteX2("00 05")), ModbusRegister(34, ByteX2("00 08")))))
  }
}

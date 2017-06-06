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
import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.junit.JUnitRunner
import org.totalgrid.modbus.data.Hex
import org.totalgrid.modbus.pdu.{ BitResponseParser, FunctionCode }

@RunWith(classOf[JUnitRunner])
class RtuAduParserTest extends FunSuite with Matchers {

  test("Valid response") {
    val rtu = new RtuAduParser(0x01)

    val pduParser = new BitResponseParser(FunctionCode.READ_DISCRETE_INPUTS.code, FunctionCode.READ_DISCRETE_INPUTS.error, 0, 1)

    val result = rtu.handleData(Hex.bufferFromHex("01 02 01 00 A1 88"), pduParser, 0)

    result match {
      case v: ValidResponse[_] =>
      case _ => false should equal(true)
    }
  }

  test("Valid exception response") {
    val rtu = new RtuAduParser(0x01)

    val pduParser = new BitResponseParser(FunctionCode.READ_DISCRETE_INPUTS.code, FunctionCode.READ_DISCRETE_INPUTS.error, 0, 1)

    val result = rtu.handleData(Hex.bufferFromHex("01 82 01 81 60"), pduParser, 0)

    result should equal(ResponseException(0x82.toByte))
  }

  test("Wrong address causes a discard") {
    val rtu = new RtuAduParser(0x0F)

    val pduParser = new BitResponseParser(FunctionCode.READ_DISCRETE_INPUTS.code, FunctionCode.READ_DISCRETE_INPUTS.error, 0, 5)

    val result = rtu.handleData(Hex.bufferFromHex("0E 02 03 04 05"), pduParser, 0)

    result should equal(Discard)
  }

  test("Bad CRC causes a discard") {
    val rtu = new RtuAduParser(0x01)

    val pduParser = new BitResponseParser(FunctionCode.READ_DISCRETE_INPUTS.code, FunctionCode.READ_DISCRETE_INPUTS.error, 0, 1)

    val result = rtu.handleData(Hex.bufferFromHex("01 02 01 00 A1 FF"), pduParser, 0)

    result should equal(Discard)
  }

  test("Bad CRC in exception causes a discard") {
    val rtu = new RtuAduParser(0x0F)

    val pduParser = new BitResponseParser(FunctionCode.READ_DISCRETE_INPUTS.code, FunctionCode.READ_DISCRETE_INPUTS.error, 0, 1)

    val result = rtu.handleData(Hex.bufferFromHex("0F 8D 02 52 FF"), pduParser, 0)

    result should equal(Discard)
  }

}
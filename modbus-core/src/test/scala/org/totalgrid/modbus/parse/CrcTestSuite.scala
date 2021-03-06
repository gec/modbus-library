package org.totalgrid.modbus.parse

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
import org.junit.runner.RunWith
import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.junit.JUnitRunner
import org.totalgrid.modbus.data.Hex

@RunWith(classOf[JUnitRunner])
class CrcTestSuite extends FunSuite with Matchers {

  import Hex._

  test("Correctly generates crc from docs") {
    val crc = Crc.calc(fromHex("02 07"))
    toHex(crc) should equal("41 12")
  }

  test("Correctly generates a crc from http://www.lammertbies.nl/comm/info/crc-calculation.html") {
    val crc = Crc.calc(fromHex("0A 0B 0C 0D 0E"))
    toHex(crc) should equal("FB 72")
  }

}
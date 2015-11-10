package org.totalgrid.modbus.impl.requests

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

import org.totalgrid.modbus.impl._

@RunWith(classOf[JUnitRunner])
class ReadCoilsRequestTestSuite extends FunSuite with ShouldMatchers {

  test("Read coils request generates correct frame") {
    val r = new ReadDiscreteInputRequest(UInt16(0x0A), UInt16(0x05))(println) // get 5 coils starting at address == 10
    toHex(r.getBytes) should equal("02 00 0A 00 05")
  }

}
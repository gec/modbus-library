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

@RunWith(classOf[JUnitRunner])
class ImplicitConversionTestSuite extends FunSuite with ShouldMatchers {

  test("ByteBufferToHex") {
    val b = ByteBuffer.wrap("0A 0B 0C")
    toHex(b) should equal("0A 0B 0C")
    b.remaining() should equal(3)
  }

  test("ByteBuffer preserve function works as expected") {
    val b = ByteBuffer.allocate(10)

    b.put(fromHex("0A 0B 0C 0D"))
    b.remaining() should equal(6)
    b.position() should equal(4)
    b.limit() should equal(10)

    b.flip()
    b.limit() should equal(4)
    b.remaining() should equal(4)
    b.position() should equal(0)

    b.get() // this should have no effect!
    b.preserve()
    b.position() should equal(4)
    b.limit() should equal(10)
    b.remaining() should equal(6)

  }

}
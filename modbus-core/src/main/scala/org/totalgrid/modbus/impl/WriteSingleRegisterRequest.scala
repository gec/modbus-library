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

import java.util.Arrays
import com.typesafe.scalalogging.slf4j.Logging

final class WriteSingleRegisterRequest(value: UInt16, address: UInt16) extends PduHandler with Logging {

  val function: FunctionCode = FunctionCode.WRITE_SINGLE_REGISTER

  def getBytes: Array[Byte] = assemble(function.code, address, value)

  def numResponseBytes: Int = 4

  def handleResponse(array: Array[Byte]): Unit = {
    // TODO better error message here
    if (!Arrays.equals(array, getBytes.slice(1, 5))) logger.warn("WriteSingleRegister response did not exactly equal request")
  }

}

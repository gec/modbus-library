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

final class ErrorCode(val code: Byte, val desc: String) {
  override def toString = "code: " + toHex(code) + " desc: " + desc
}

object ErrorCode {

  val INVALID_FUNCTION_CODE = new ErrorCode(0x01, "Invalid function code")
  val INVALID_DATA_ADDRESS = new ErrorCode(0x02, "Invalid data address")
  val INVALID_DATA_VALUE = new ErrorCode(0x03, "Invalid data value")
  val INVALID_MB_FUNCION_4 = new ErrorCode(0x04, "Invalid MB function 4")
  val INVALID_MB_FUNCION_5 = new ErrorCode(0x05, "Invalid MB function 5")
  val INVALID_MB_FUNCION_6 = new ErrorCode(0x06, "Invalid MB function 6")

  def apply(code: Byte) = code match {
    case INVALID_FUNCTION_CODE.code => INVALID_FUNCTION_CODE
    case INVALID_DATA_ADDRESS.code => INVALID_DATA_ADDRESS
    case INVALID_DATA_VALUE.code => INVALID_DATA_VALUE
    case INVALID_MB_FUNCION_4.code => INVALID_MB_FUNCION_4
    case INVALID_MB_FUNCION_5.code => INVALID_MB_FUNCION_5
    case INVALID_MB_FUNCION_6.code => INVALID_MB_FUNCION_6
    case x: Byte => new ErrorCode(x, "Unknown error code")
  }

}

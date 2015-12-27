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

import java.nio.ByteBuffer

import org.totalgrid.modbus.data.BufferSerializable

case class FunctionCode(code: Byte) extends BufferSerializable {
  val error = (code | 0x80).toByte

  def size(): Int = 1

  def write(buffer: ByteBuffer): Unit = {
    buffer.put(code)
  }
}

object FunctionCode {
  val READ_DISCRETE_INPUTS = new FunctionCode(0x02)
  val READ_COILS = new FunctionCode(0x01)
  val WRITE_SINGLE_COIL = new FunctionCode(0x05)
  val WRITE_MULTIPLE_COILS = new FunctionCode(0x0F)
  val READ_INPUT_REGISTERS = new FunctionCode(0x04)
  val READ_HOLDING_REGISTERS = new FunctionCode(0x03)
  val WRITE_SINGLE_REGISTER = new FunctionCode(0x06)
  val WRITE_MULTIPLE_REGISTERS = new FunctionCode(0x10)
}
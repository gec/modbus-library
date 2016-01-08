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

import com.typesafe.scalalogging.slf4j.Logging
import org.totalgrid.modbus.{ ByteX2, ModbusRegister, ModbusBit }
import org.totalgrid.modbus.data.UInt16
import org.totalgrid.modbus.parse._

class ReadDiscreteInputRequest(start: UInt16, count: UInt16)
  extends ReadBitsRequest(start, count, FunctionCode.READ_DISCRETE_INPUTS)

class ReadCoilsRequest(start: UInt16, count: UInt16)
  extends ReadBitsRequest(start, count, FunctionCode.READ_COILS)

class ReadInputRegisterRequest(start: UInt16, count: UInt16)
  extends ReadUInt16Request(start, count, FunctionCode.READ_INPUT_REGISTERS)

class ReadHoldingRegisterRequest(start: UInt16, count: UInt16)
  extends ReadUInt16Request(start, count, FunctionCode.READ_HOLDING_REGISTERS)

abstract class ReadBitsRequest(start: UInt16, count: UInt16, code: FunctionCode)
  extends StartCountReadRequest(start, count, code)

abstract class ReadUInt16Request(start: UInt16, count: UInt16, code: FunctionCode)
  extends StartCountReadRequest(start, count, code)

class BitResponseParser(val function: Byte, val error: Byte, start: Int, count: Int) extends PduParser[Seq[ModbusBit]] with Logging {

  def handleData(buffer: ByteBuffer): ParseState[Seq[ModbusBit]] = {
    val expectedSize = responseSize()

    if (buffer.remaining() < expectedSize) {
      Preserve
    } else {
      val payloadSize = buffer.get()
      if (payloadSize != (expectedSize - 1)) {
        logger.warn("Expected " + (expectedSize - 1) + " bytes, but packet specifies " + payloadSize)
        Discard
      } else {
        val array = new Array[Byte](payloadSize)
        buffer.get(array)
        val bits = BitReader(start, count, 0, array)((index, v) => new ModbusBit(index, v))
        ValidResponse(bits)
      }
    }
  }

  def responseSize(): Int = {
    val div = count / 8
    val mod = count % 8
    if (mod == 0) div + 1 else div + 2
  }

}

class RegisterResponseParser(val function: Byte, val error: Byte, start: Int, count: Int) extends PduParser[Seq[ModbusRegister]] with Logging {

  def handleData(buffer: ByteBuffer): ParseState[Seq[ModbusRegister]] = {
    val expectedSize = responseSize()
    if (buffer.remaining() < expectedSize) {
      Preserve
    } else {
      val payloadSize = buffer.get()
      if (payloadSize != (expectedSize - 1)) {
        logger.warn("Expected " + (expectedSize - 1) + " bytes, but packet specifies " + payloadSize)
        Discard
      } else {
        val registers = Range(0, count).map { i =>
          ModbusRegister(start + i, ByteX2(buffer.get(), buffer.get()))
        }.toVector
        ValidResponse(registers)
      }
    }
  }

  def responseSize(): Int = 2 * count + 1
}
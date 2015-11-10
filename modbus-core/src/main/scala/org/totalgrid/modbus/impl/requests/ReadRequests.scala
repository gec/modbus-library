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

import org.totalgrid.modbus.impl._
import org.totalgrid.modbus._
import com.typesafe.scalalogging.slf4j.Logging

class ReadDiscreteInputRequest(start: UInt16, count: UInt16)(fun: List[ModbusBit] => Unit)
  extends ReadBitsRequest(start, count, FunctionCode.READ_DISCRETE_INPUTS)(fun)

class ReadCoilsRequest(start: UInt16, count: UInt16)(fun: List[ModbusBit] => Unit)
  extends ReadBitsRequest(start, count, FunctionCode.READ_COILS)(fun)

class ReadInputRegisterRequest(start: UInt16, count: UInt16)(fun: List[ModbusRegister] => Unit)
  extends ReadUInt16Request(start, count, FunctionCode.READ_INPUT_REGISTERS)(fun)

class ReadHoldingRegisterRequest(start: UInt16, count: UInt16)(fun: List[ModbusRegister] => Unit)
  extends ReadUInt16Request(start, count, FunctionCode.READ_HOLDING_REGISTERS)(fun)

abstract class ReadBitsRequest(start: UInt16, count: UInt16, code: FunctionCode)(fun: List[ModbusBit] => Unit)
    extends StartCountReadRequest(start, count, code) with Logging {

  def handleResponse(array: Array[Byte]): Unit = {
    assert(array.size >= numResponseBytes)
    val numBytes = array(0) //read one byte out of the buffer
    val expected = numResponseBytes - 1
    if (numBytes != expected) logger.error("Expected " + expected + " bytes, but packet specifies " + numBytes)
    else {
      val list = BitReader(start.value, count.value, 1, array)((int, bool) => new ModbusBit(int, bool))
      fun(list)
    }
  }

  def numResponseBytes: Int = {
    val div = count.value / 8
    val mod = count.value % 8
    if (mod == 0) div + 1 else div + 2
  }
}

abstract class ReadUInt16Request(start: UInt16, count: UInt16, code: FunctionCode)(fun: List[ModbusRegister] => Unit)
    extends StartCountReadRequest(start, count, code) with Logging {

  def handleResponse(array: Array[Byte]): Unit = {
    assert(array.size >= numResponseBytes)
    val numBytes = array(0) //read one byte out of the buffer
    val expected = numResponseBytes - 1
    if (numBytes != expected) logger.error("Expected " + expected + " bytes, but packet specifies " + numBytes)
    else {
      var index = start.value
      val integers = array.takeRight(expected).grouped(2).map { (arr) =>
        val ir = ModbusRegister(index, ByteX2(arr(0), arr(1)))
        index += 1
        ir
      }
      fun(integers.toList)
    }
  }

  def numResponseBytes: Int = 2 * count.value + 1

}
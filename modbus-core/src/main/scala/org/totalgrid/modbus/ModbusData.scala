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
package org.totalgrid.modbus

import java.nio.ByteBuffer

import org.totalgrid.modbus.data.Hex

object ModbusData {
  def joinSInt32BE(first: ByteX2, second: ByteX2): Long = first.sInt16.toLong << 16 | second.uInt16
  def joinUInt32BE(first: ByteX2, second: ByteX2): Long = first.uInt16.toLong << 16 | second.uInt16

  def joinSInt32LE(first: ByteX2, second: ByteX2): Long = joinSInt32BE(second, first)
  def joinUInt32LE(first: ByteX2, second: ByteX2): Long = joinUInt32BE(second, first)

  def joinFloat32BE(first: ByteX2, second: ByteX2): Float = ByteBuffer.wrap(Array(first.first, first.second, second.first, second.second)).getFloat
  def joinFloat32LE(first: ByteX2, second: ByteX2): Float = joinFloat32BE(second, first)

  def joinFloat64BE(first: ByteX2, second: ByteX2, third: ByteX2, fourth: ByteX2): Double =
    ByteBuffer.wrap(Array(first.first, first.second, second.first, second.second, third.first, third.second, fourth.first, fourth.second)).getDouble
  def joinFloat64LE(first: ByteX2, second: ByteX2, third: ByteX2, fourth: ByteX2): Double = joinFloat64BE(fourth, third, second, first)
}

sealed trait ModbusData[A] {
  def index: Int
  def value: A
}

final case class ModbusBit(i: Int, b: Boolean) extends ModbusData[Boolean] {
  final def index: Int = i
  final def value: Boolean = b
}

object ByteX2 {
  def apply(string: String): ByteX2 = {
    val bytes = Hex.fromHex(string)
    if (bytes.size != 2) throw new IllegalArgumentException("2 and only 2 bytes allowed")
    else ByteX2(bytes(0), bytes(1))
  }
}

final case class ByteX2(first: Byte, second: Byte) {
  def sInt16: Int = (first << 8) | (second & 0xFF)
  def uInt16: Int = ((first & 0xFF.toInt) << 8) | (second & 0xFF.toInt)
}

final case class ModbusRegister(i: Int, bx2: ByteX2) extends ModbusData[ByteX2] {
  def index: Int = i
  def value: ByteX2 = bx2
}
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
package org.totalgrid.modbus.data

import java.nio.ByteBuffer

object UInt16 {

  def writeShort(buffer: ByteBuffer, value: Short): Unit = {
    buffer.put(((value >> 8) & 0xFF).toByte)
    buffer.put((value & 0xFF).toByte)
  }
}
case class UInt16(value: Int) extends BufferSerializable {
  if (value < 0 || value > 65535) {
    throw new IllegalArgumentException(s"Value $value out of bounds for a 16-bit unsigned integer")
  }

  def size(): Int = 2

  def write(buffer: ByteBuffer): Unit = {
    buffer.put(((value >> 8) & 0xFF).toByte)
    buffer.put((value & 0xFF).toByte)
  }
}

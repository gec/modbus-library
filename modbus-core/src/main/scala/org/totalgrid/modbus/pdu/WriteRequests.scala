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
import java.util

import com.typesafe.scalalogging.slf4j.Logging
import org.totalgrid.modbus.data.{ BufferSerializable, SerializableSequence, UInt16 }
import org.totalgrid.modbus.parse._

trait WriteRequest extends RequestPdu {
  def parser(): WriteResponseParser
}

class WriteSingleCoilRequest(value: Boolean, address: UInt16) extends WriteRequest with SerializableSequence {
  val function: FunctionCode = FunctionCode.WRITE_SINGLE_COIL

  private def valueBits = if (value) UInt16(0xFF00) else UInt16(0x0000)

  protected def objects(): Seq[BufferSerializable] = {
    Vector(function, address, valueBits)
  }

  def parser(): WriteResponseParser = {
    val ar = new Array[Byte](this.size() - 1)
    val bb = ByteBuffer.wrap(ar)
    SerializableSequence.write(bb, objects().drop(1))
    new WriteResponseParser(function.code, function.error, ar)
  }
}

class WriteSingleRegisterRequest(value: UInt16, address: UInt16) extends WriteRequest with SerializableSequence {
  val function: FunctionCode = FunctionCode.WRITE_SINGLE_REGISTER

  protected def objects(): Seq[BufferSerializable] = {
    Vector(function, address, value)
  }

  def parser(): WriteResponseParser = {
    val ar = new Array[Byte](this.size() - 1)
    val bb = ByteBuffer.wrap(ar)
    SerializableSequence.write(bb, objects().drop(1))
    new WriteResponseParser(function.code, function.error, ar)
  }
}

class WriteMaskRegisterRequest(andMask: UInt16, orMask: UInt16, address: UInt16) extends WriteRequest with SerializableSequence {
  val function: FunctionCode = FunctionCode.MASK_WRITE_REGISTER

  protected def objects(): Seq[BufferSerializable] = {
    Vector(function, address, andMask, orMask)
  }

  def parser(): WriteResponseParser = {
    val ar = new Array[Byte](this.size() - 1)
    val bb = ByteBuffer.wrap(ar)
    SerializableSequence.write(bb, objects().drop(1))
    new WriteResponseParser(function.code, function.error, ar)
  }
}

class WriteResponseParser(val function: Byte, val error: Byte, requestBytes: Array[Byte]) extends PduParser[Boolean] with Logging {

  def responseSize(): Int = requestBytes.length

  def handleData(buffer: ByteBuffer): ParseState[Boolean] = {
    if (buffer.remaining() < requestBytes.length) {
      Preserve
    } else {
      val copy = new Array[Byte](requestBytes.length)
      buffer.get(copy)
      if (util.Arrays.equals(requestBytes, copy)) {
        ValidResponse(true)
      } else {
        logger.warn("Write response did not exactly match request")
        Discard
      }
    }
  }
}

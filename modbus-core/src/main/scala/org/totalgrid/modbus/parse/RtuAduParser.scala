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
package org.totalgrid.modbus.parse

import java.nio.ByteBuffer

import com.typesafe.scalalogging.LazyLogging
import org.totalgrid.modbus.data.UInt16
import org.totalgrid.modbus.pdu.RequestPdu

class RtuAduParser(address: Byte) extends AduParser with LazyLogging {

  def aduSize(): Int = 3 // 1 address, 2 CRC

  def handleData[Result](buffer: ByteBuffer, pduParser: PduParser[Result], sequence: Int): ParseState[Result] = {

    def processResponse(): ParseState[Result] = {
      if (buffer.remaining() < pduParser.responseSize() + 2) {
        Preserve
      } else {
        val crc = buffer.getShort(buffer.position() + pduParser.responseSize())
        val calc = Crc.calc(buffer, pduParser.responseSize() + 2)
        if (crc == calc) {
          pduParser.handleData(buffer)
        } else {
          logger.warn("Crc failure in response frame, received " + crc + " but expected " + calc)
          Discard
        }
      }
    }

    def processException(v: Byte): ParseState[Result] = {
      val crc = buffer.getShort(3)
      val calc = Crc.calc(buffer, 3)
      if (crc != calc) {
        logger.warn("Crc failure in exception frame")
        Discard
      } else {
        ResponseException(v)
      }
    }

    def processType(): ParseState[Result] = {
      val b = buffer.get()
      b match {
        case pduParser.function => processResponse()
        case pduParser.error => processException(b)
        case x: Byte =>
          logger.error("Unexpected function code: " + x + "  while processing " + pduParser.function)
          Discard
      }
    }

    def processAddress(): ParseState[Result] = {
      if (buffer.get() == address) {
        processType()
      } else {
        Discard
      }
    }

    // 5 is magic number, minimum modbus size
    if (buffer.remaining() < 5) {
      Preserve
    } else {
      processAddress()
    }
  }

  def writePdu(buffer: ByteBuffer, request: RequestPdu, sequence: Int): Unit = {
    val pduSize = request.size()

    buffer.put(address)

    buffer.mark()
    val prePayloadPos = buffer.position()
    request.write(buffer)

    val afterPayloadPos = buffer.position()
    buffer.flip()
    buffer.position(prePayloadPos)
    val crc = Crc.calc(buffer, pduSize)

    buffer.clear()
    buffer.position(afterPayloadPos)

    UInt16.writeShort(buffer, crc)
  }
}

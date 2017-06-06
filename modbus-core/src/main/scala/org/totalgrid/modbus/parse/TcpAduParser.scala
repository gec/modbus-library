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
import org.totalgrid.modbus.ByteX2
import org.totalgrid.modbus.data.{ BufferSerializable, SerializableSequence, UInt16 }
import org.totalgrid.modbus.pdu.RequestPdu

class TcpAduParser(unitIdentifier: Byte) extends AduParser with LazyLogging {

  def aduSize(): Int = 7 // "MBAP" header

  def handleData[Result](buffer: ByteBuffer, pduParser: PduParser[Result], sequence: Int): ParseState[Result] = {
    def processHeader() = {
      if (buffer.remaining() < 7) {
        Preserve
      } else {
        val respSeq = ByteX2(buffer.get(), buffer.get()).uInt16
        val respProtocol = ByteX2(buffer.get(), buffer.get()).uInt16
        val respLength = ByteX2(buffer.get(), buffer.get()).uInt16
        val respDevice = buffer.get()

        if (respSeq != sequence || respProtocol != 0 || respDevice != unitIdentifier) {
          Discard
        } else if (buffer.remaining() < (respLength - 1)) {
          Preserve
        } else {
          processFunction()
        }
      }
    }

    def processFunction() = {
      val func = buffer.get()
      func match {
        case pduParser.function => processResponse()
        case pduParser.error => processException(func)
        case x: Byte =>
          logger.error("Unexpected function code: " + x + "  while processing " + pduParser.function)
          Discard
      }
    }

    def processResponse() = {
      pduParser.handleData(buffer)
    }

    def processException(func: Byte) = {
      ResponseException(func)
    }

    processHeader()
  }

  def writePdu(buffer: ByteBuffer, request: RequestPdu, sequence: Int): Unit = {
    val pduSize = request.size()

    val objects = Vector(
      UInt16(sequence),
      UInt16(0),
      UInt16(pduSize + 1),
      BufferSerializable.wrap(unitIdentifier),
      request)

    SerializableSequence.write(buffer, objects)
  }
}

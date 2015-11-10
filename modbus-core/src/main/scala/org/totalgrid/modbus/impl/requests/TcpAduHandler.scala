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
package org.totalgrid.modbus.impl.requests

import net.agileautomata.executor4s._
import java.nio.ByteBuffer
import net.agileautomata.executor4s.TimeInterval
import org.totalgrid.modbus.impl._
import org.totalgrid.modbus.ByteX2
import com.typesafe.scalalogging.slf4j.Logging

class TcpAduHandler(sequence: Int, request: PduHandler, unitIdentifier: Byte, timeoutMs: Long) extends AduHandler with Logging {

  def getTimeout: TimeInterval = timeoutMs.milliseconds

  def getBytes: Array[Byte] = {
    val pduBytes = request.getBytes
    val length = pduBytes.length + 1

    assemble(UInt16(sequence), UInt16(0), UInt16(length), unitIdentifier.toByte, pduBytes)
  }

  def handleData(buffer: ByteBuffer): ResponseStatus = {

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
      buffer.get() match {
        case request.function.code => processResponse()
        case request.function.error => processException()
        case x: Byte =>
          logger.error("Unexpected function code: " + toHex(x) + "  while processing " + request.function)
          Discard
      }
    }

    def processResponse() = {
      val array = new Array[Byte](request.numResponseBytes)
      buffer.get(array)
      request.handleResponse(array)
      ValidResponse
    }

    def processException() = {
      ResponseException(buffer.get())
    }

    processHeader()
  }
}

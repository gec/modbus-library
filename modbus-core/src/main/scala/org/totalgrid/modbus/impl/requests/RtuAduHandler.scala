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
import net.agileautomata.executor4s._
import java.nio.ByteBuffer
import org.totalgrid.modbus.impl._
import com.typesafe.scalalogging.slf4j.Logging

class RtuAduHandler(request: PduHandler, address: Byte, timeoutMs: Long) extends AduHandler with Logging {

  final override def getTimeout = timeoutMs.milliseconds

  final override def getBytes() = {
    val body = assemble(address, request.getBytes)
    assemble(body, Crc.calc(body))
  }

  final def handleData(buffer: ByteBuffer): ResponseStatus = {

    def processAck(): ResponseStatus = {
      if (buffer.remaining() < request.numResponseBytes + 2) Preserve
      else {
        val crc = buffer.getShort(buffer.position() + request.numResponseBytes)
        val calc = Crc.calc(buffer, request.numResponseBytes + 2)
        if (crc == calc) {
          val array = new Array[Byte](request.numResponseBytes)
          buffer.get(array)
          request.handleResponse(array)
          ValidResponse
        } else {
          logger.warn("Crc failure in response frame, received " + crc + " but expected " + calc)
          Discard
        }
      }
    }

    def processException(): ResponseStatus = {
      val crc = buffer.getShort(3)
      val calc = Crc.calc(buffer, 3)
      if (crc != calc) {
        logger.warn("Crc failure in exception frame")
        Discard
      } else {
        ResponseException(buffer.get())
      }
    }

    def processType(): ResponseStatus = buffer.get() match {
      case request.function.code => processAck()
      case request.function.error => processException()
      case x: Byte =>
        logger.error("Unexpected function code: " + toHex(x) + "  while processing " + request.function)
        Discard
    }

    def processAddress(): ResponseStatus = {
      if (buffer.get() == address) processType()
      else Discard
    }

    if (buffer.remaining() < 5) Preserve // 5 is magic number, minimum modbus size
    else processAddress()
  }

}
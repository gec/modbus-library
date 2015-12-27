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
package org.totalgrid.modbus.process

import java.nio.ByteBuffer

import com.typesafe.scalalogging.slf4j.Logging
import org.totalgrid.modbus.{ ModbusProtocolError, ModbusExceptionResponse }
import org.totalgrid.modbus.parse._

import scala.concurrent.Promise

trait ResponseHandler {
  def handleData(buffer: ByteBuffer): Boolean
}

class ReadResponseHandler[Result](stackId: String,
    localReadBuffer: ByteBuffer,
    sequence: Int,
    dispatcher: Dispatcher,
    aduParser: AduParser,
    parser: PduParser[Result],
    resultHandler: Result => Unit,
    promOpt: Option[Promise[Result]]) extends ResponseHandler with Logging {
  localReadBuffer.clear()

  def handleData(buffer: ByteBuffer): Boolean = {

    localReadBuffer.put(buffer)
    localReadBuffer.flip()

    aduParser.handleData(localReadBuffer, parser, sequence) match {
      case Preserve =>
        localReadBuffer.position(localReadBuffer.limit())
        localReadBuffer.limit(localReadBuffer.capacity())
        false
      case Discard =>
        logger.warn(s"$stackId discarding data")
        promOpt.foreach(_.failure(new ModbusProtocolError("Response could not be parsed")))
        true
      case ResponseException(errorCode) =>
        logger.warn(s"$stackId response exception on read: " + errorCode)
        promOpt.foreach(_.failure(new ModbusExceptionResponse(errorCode)))
        true
      case ValidResponse(result) =>
        logger.trace(s"$stackId got valid response, seq: $sequence")
        dispatcher.dispatch(() => resultHandler(result))
        promOpt.foreach(_.success(result))
        true
    }
  }
}

class WriteResponseHandler(stackId: String, localReadBuffer: ByteBuffer, sequence: Int, aduParser: AduParser, parser: PduParser[Boolean], promise: Promise[Boolean]) extends ResponseHandler with Logging {
  localReadBuffer.clear()

  def handleData(buffer: ByteBuffer): Boolean = {

    localReadBuffer.put(buffer)
    localReadBuffer.flip()

    aduParser.handleData(localReadBuffer, parser, sequence) match {
      case Preserve =>
        localReadBuffer.position(localReadBuffer.limit())
        localReadBuffer.limit(localReadBuffer.capacity())
        false
      case Discard =>
        logger.warn(s"$stackId discarding data")
        true
      case ResponseException(errorCode) =>
        logger.warn(s"$stackId response exception on write: " + errorCode)
        true
      case ValidResponse(result) =>
        logger.trace(s"$stackId got valid response, seq: $sequence")
        promise.success(result)
        true
    }

  }
}

package org.totalgrid.modbus.impl

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
import org.totalgrid.modbus.impl.requests.{ TcpAduHandler, RtuAduHandler }

// TODO -documentation parsing response codes and state machine
sealed trait ResponseStatus
final case object Preserve extends ResponseStatus
final case object Discard extends ResponseStatus
final case object ValidResponse extends ResponseStatus
final case class ResponseException(ex: Byte) extends ResponseStatus

final class ResponseTimeoutException(timeout: TimeInterval)
  extends RuntimeException("Response timed out after " + timeout)

object AduHandlerFactory {

  def rtu(address: Byte): AduHandlerFactory = new AduHandlerFactory {
    def apply(sequence: Int, pdu: PduHandler, timeoutMs: Long) = new RtuAduHandler(pdu, address, timeoutMs)
  }

  def tcpip(address: Byte): AduHandlerFactory = new AduHandlerFactory {
    def apply(sequence: Int, pdu: PduHandler, timeoutMs: Long): AduHandler = new TcpAduHandler(sequence, pdu, address, timeoutMs)
  }

}

trait AduHandlerFactory {
  def apply(sequence: Int, pdu: PduHandler, timeoutMs: Long): AduHandler
}

trait AduHandler {
  def getBytes: Array[Byte]
  def getTimeout: TimeInterval

  def handleData(buffer: ByteBuffer): ResponseStatus
}

trait RequestCallback {
  def onSuccess(): Unit
  def onChannelClosed(): Unit
  def onFailure(ex: Exception): Unit
  def onTimeout(): Unit

  final def onFailure(msg: String): Unit = onFailure(new Exception(msg))
}

case class ActiveRequest(factory: AduHandler, callback: RequestCallback, timer: Cancelable)

case class DelayedRequest(factory: AduHandler, callback: RequestCallback)


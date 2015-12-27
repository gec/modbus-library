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
package org.totalgrid.modbus.poll

import org.totalgrid.modbus.data.UInt16
import org.totalgrid.modbus.pdu._

sealed trait Poll {
  val timeoutMs: Long
  val intervalMs: Long
  def pdu(): RequestPdu
}

case class ReadDiscreteInput(start: UInt16, count: UInt16, intervalMs: Long, timeoutMs: Long) extends Poll {
  def pdu(): RequestPdu = new ReadDiscreteInputRequest(start, count)
  def resultHandler(): BitResponseParser = {
    val code = FunctionCode.READ_DISCRETE_INPUTS
    new BitResponseParser(code.code, code.error, start.value, count.value)
  }
}

case class ReadCoilStatus(start: UInt16, count: UInt16, intervalMs: Long, timeoutMs: Long) extends Poll {
  def pdu(): RequestPdu = new ReadCoilsRequest(start, count)
  def resultHandler(): BitResponseParser = {
    val code = FunctionCode.READ_COILS
    new BitResponseParser(code.code, code.error, start.value, count.value)
  }
}

case class ReadInputRegister(start: UInt16, count: UInt16, intervalMs: Long, timeoutMs: Long) extends Poll {
  def pdu(): RequestPdu = new ReadInputRegisterRequest(start, count)
  def resultHandler(): RegisterResponseParser = {
    val code = FunctionCode.READ_INPUT_REGISTERS
    new RegisterResponseParser(code.code, code.error, start.value, count.value)
  }
}

case class ReadHoldingRegister(start: UInt16, count: UInt16, intervalMs: Long, timeoutMs: Long) extends Poll {
  def pdu(): RequestPdu = new ReadHoldingRegisterRequest(start, count)
  def resultHandler(): RegisterResponseParser = {
    val code = FunctionCode.READ_HOLDING_REGISTERS
    new RegisterResponseParser(code.code, code.error, start.value, count.value)
  }
}


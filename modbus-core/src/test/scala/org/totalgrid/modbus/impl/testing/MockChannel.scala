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
package org.totalgrid.modbus.impl.testing

import net.agileautomata.nio4s.Channel
import java.nio.ByteBuffer

import net.agileautomata.executor4s._

import org.totalgrid.modbus.impl._
import net.agileautomata.executor4s.testing.{ MockExecutor, MockFuture }

class MockChannel(var open: Boolean = true) extends Channel {

  val writeQueue = collection.mutable.Queue.empty[Array[Byte]]

  case class ReadOp(buffer: ByteBuffer, future: MockFuture[Result[ByteBuffer]])

  var read: Option[ReadOp] = None

  private val executor = new MockExecutor

  def writes: List[String] = writeQueue.map(toHex).toList

  def respond(hex: String)(verify: ByteBuffer => Unit) = read match {
    case Some(op) =>
      read = None
      op.buffer.put(fromHex(hex))
      op.future.set(Success(op.buffer))
    case None => throw new Exception("No read in progress")
  }

  def getExecutor: Executor = executor

  def isOpen: Boolean = open

  def close(): Result[Unit] = {
    open = false
    read.foreach(_.future.set(Failure("Channel closed during read operation")))
    read = None
    Success[Unit]()
  }

  def read(buffer: ByteBuffer): Future[Result[ByteBuffer]] = {
    if (isOpen) {
      read match {
        case Some(x) =>
          MockFuture.defined(Failure("A read operation is already in progress"))
        case None =>
          val f = MockFuture.undefined[Result[ByteBuffer]]
          read = Some(ReadOp(buffer, f))
          f
      }
    } else {
      MockFuture.defined(Failure("Read request was made while the layer was closed"))
    }
  }

  def write(buffer: ByteBuffer): Future[Result[Int]] = {
    def getResult() = {
      if (isOpen) {
        val arr = new Array[Byte](buffer.remaining())
        buffer.get(arr)
        writeQueue.enqueue(arr)
        Success(arr.size)
      } else Failure("MockChannel is closed")
    }
    MockFuture.defined(getResult())
  }
}
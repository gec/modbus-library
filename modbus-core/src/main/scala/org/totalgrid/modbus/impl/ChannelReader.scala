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
import java.nio.ByteBuffer
import net.agileautomata.nio4s._
import net.agileautomata.executor4s._
import com.typesafe.scalalogging.slf4j.Logging

/**
 * Keeps reading streaming to
 */
private[modbus] object ChannelReader extends Logging {

  private def onRead(channel: Channel, onData: ByteBuffer => Unit)(result: Result[ByteBuffer]): Unit = result match {
    case Success(buffer) =>
      buffer.flip()
      onData(buffer)
      apply(channel, buffer)(onData)
    case Failure(ex) =>
      logger.error("Error reading", ex)
  }

  def apply(channel: Channel, buffer: ByteBuffer)(onData: ByteBuffer => Unit) =
    channel.read(buffer).listen(onRead(channel, onData))
}
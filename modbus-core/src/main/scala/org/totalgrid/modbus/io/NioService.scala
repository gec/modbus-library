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
package org.totalgrid.modbus.io

import java.nio.ByteBuffer

trait ChannelUser {
  def onData(buffer: ByteBuffer): Unit
  def onClosed(): Unit
}

trait ClientConnection {
  def connect(host: String, port: Int, timeoutMs: Long): Unit
  def send(buffer: ByteBuffer): Unit
  def close(): Unit
}

trait ConnectionSource {
  def connect(host: String, port: Int, timeoutMs: Long, user: ChannelUser): ClientConnection
}

class NioService(readBufferSize: Int) extends ConnectionSource {

  private val selectorManager = new NioSelectorManager(readBufferSize)

  def connect(host: String, port: Int, timeoutMs: Long, user: ChannelUser): ClientConnection = {
    val connection = new NioChannel(user, selectorManager.channelService())

    try {
      connection.connect(host, port, timeoutMs)
      connection
    } catch {
      case ex: Throwable =>
        connection.close()
        throw ex
    }
  }

  def run(): Unit = {
    selectorManager.run()
  }

  def close(): Unit = {
    selectorManager.close()
  }
}

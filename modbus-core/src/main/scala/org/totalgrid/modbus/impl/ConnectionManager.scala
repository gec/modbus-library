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
import net.agileautomata.nio4s._
import net.agileautomata.executor4s._
import org.totalgrid.modbus._

object ConnectionManager {
  case class Config(minConnectDelayMs: Long, maxConnectDelayMs: Long)
}

private[modbus] class ConnectionManager(factory: ChannelFactory, listener: ChannelListener, config: ConnectionManager.Config) {

  private def onException(ex: Exception): Unit = {
    listener.onException(ex)
    restart(config.minConnectDelayMs)
  }

  private def onConnect(delayMs: Long)(result: Result[Channel]): Unit = result match {
    case Success(channel) =>
      channel.listen(onException)
      listener.onChannelOpen(channel)
    case Failure(ex) =>
      val delay = math.min(2 * delayMs, config.maxConnectDelayMs)
      listener.onConnectionFailure(ex, delay)
      restart(delay)
  }

  private def connect(delayms: Long) = {
    val result = factory.open
    listener.onChannelOpening()
    result.listen(onConnect(delayms))
  }

  protected def restart(delayms: Long) = factory.strand.schedule(delayms.milliseconds)(connect(delayms))

  connect(config.minConnectDelayMs)
}
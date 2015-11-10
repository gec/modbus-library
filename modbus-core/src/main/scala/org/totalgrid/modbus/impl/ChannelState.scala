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
package org.totalgrid.modbus.impl

import net.agileautomata.nio4s.Channel
import collection.immutable.Queue
import java.nio.ByteBuffer
import annotation.tailrec
import com.typesafe.scalalogging.slf4j.Logging

private[impl] object ChannelState {
  def apply(): ChannelState = ChannelState(None, None, Queue.empty[DelayedRequest])
}

private[impl] final case class ChannelState(channel: Option[Channel], active: Option[ActiveRequest], delayed: Queue[DelayedRequest]) extends Logging {

  def enqueue(factory: AduHandler, callback: RequestCallback): ChannelState =
    this.copy(delayed = delayed.enqueue(DelayedRequest(factory, callback)))

  def complete(cancel: Boolean): ChannelState = active match {
    case Some(x) =>
      if (cancel) x.timer.cancel()
      this.copy(active = None)
    case None => throw new Exception("No active request")
  }

  def onChannelOpen(ch: Channel) = this.copy(channel = Some(ch))

  def onChannelClose(): ChannelState = {
    val s = if (active.isDefined) complete(true) else this
    s.copy(channel = None).startDelayed
  }

  /**
   * startDelayed a request from the delayed queue, or return the object unmodified if no delayed requests are present
   */
  @tailrec
  def startDelayed: ChannelState = active match {
    case Some(a) => this
    case None =>
      if (delayed.isEmpty) this
      else {
        val (req, q) = delayed.dequeue
        channel match {
          case Some(ch) =>
            logger.debug("Wrote: " + toHex(req.factory.getBytes))
            ch.write(ByteBuffer.wrap(req.factory.getBytes)).await.get //synchronous write
            val timer = ch.getExecutor.schedule(req.factory.getTimeout)(req.callback.onTimeout)
            this.copy(active = Some(ActiveRequest(req.factory, req.callback, timer)), delayed = q)
          case None =>
            req.callback.onChannelClosed()
            this.copy(active = None, delayed = q).startDelayed
        }
      }
  }

}

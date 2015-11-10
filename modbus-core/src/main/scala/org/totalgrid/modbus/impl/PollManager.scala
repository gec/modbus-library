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

import org.totalgrid.modbus._
import com.typesafe.scalalogging.slf4j.Logging
import net.agileautomata.executor4s._

object RequestRecordOrdering extends Ordering[RequestRecord] {
  def compare(a: RequestRecord, b: RequestRecord) = {
    if (a.nextExeMs > b.nextExeMs) 1
    else if (b.nextExeMs < a.nextExeMs) -1
    else 0
  }
}

private[modbus] case class RequestRecord(poll: Poll, observer: ModbusDeviceObserver, retries: Int, nextExeMs: Long) {
  def reset(numRetries: Int, time: Long) = this.copy(nextExeMs = time + poll.intervalMs, retries = numRetries)
}

private[modbus] class RequestSet(seq: Seq[RequestRecord]) {
  assert(!seq.isEmpty)
  private val set = collection.mutable.Set.empty[RequestRecord]
  seq.foreach(set.add)

  def add(r: RequestRecord) = set.add(r)

  def next(): Option[RequestRecord] = if (set.isEmpty) None else {
    val min = set.min(RequestRecordOrdering)
    set.remove(min)
    Some(min)
  }
}

/**
 * Listens to a channel manager and polls a device
 */
private[modbus] final class PollManager(
    aduExecutor: AduExecutor,
    observer: ModbusDeviceObserver,
    retries: Int,
    exe: Executor,
    polls: List[Poll],
    factory: AduHandlerFactory,
    seqGen: SequenceManager,
    ts: TimeSource = SystemTimeSource) extends Logging {

  private val set = new RequestSet(polls.map(p => RequestRecord(p, observer, retries, 0)))

  executeNext()

  private def executeNext() {

    val time = ts.currentTimeMillis()

    def onResponse(t: RequestRecord)(rsp: RequestResponse) = {

      rsp match {
        case SuccessResponse =>
          t.observer.onCommSuccess()
          set.add(t.reset(retries, ts.currentTimeMillis()))
          executeNext()
        case ExceptionResponse(ex) =>
          ex match {
            case rte: ResponseTimeoutException => logger.info("Poll failed: " + rte.getMessage)
            case _ => logger.error("Poll failed: " + ex.getMessage, ex)
          }
          val next = if (t.retries == 0) {
            t.observer.onCommFailure()
            t.reset(retries, ts.currentTimeMillis())
          } else t.reset(t.retries - 1, ts.currentTimeMillis())
          set.add(next)
          executeNext()
        case ChannelClosedResponse =>
      }
    }

    def execute(task: RequestRecord) = {
      val handler = factory(seqGen.next(), task.poll.pdu(observer), task.poll.timeoutMs)
      aduExecutor.execute(handler).listen(onResponse(task))
    }

    set.next().foreach { task =>
      if (time >= task.nextExeMs) execute(task)
      else {
        val nextTime = task.nextExeMs - time
        exe.schedule(nextTime.milliseconds)(execute(task))
      }
    }
  }

}
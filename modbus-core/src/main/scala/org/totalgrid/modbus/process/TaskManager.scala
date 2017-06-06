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

import com.typesafe.scalalogging.LazyLogging
import org.totalgrid.modbus.poll.Poll

import scala.collection.immutable.Queue

class TaskManager(polls: Seq[Poll]) extends LazyLogging {
  private var taskQueue: Queue[(Option[Long], Poll)] = {
    polls.map(p => (None, p)).to[Queue]
  }

  def timeUntilNext(): Long = {
    if (taskQueue.isEmpty) {
      Long.MaxValue
    } else {
      val now = System.currentTimeMillis()
      taskQueue.head match {
        case (None, poll) => 0
        case (Some(last), poll) => (last + poll.intervalMs) - now
      }
    }
  }

  def next(): Poll = {
    val now = System.currentTimeMillis()
    logger.trace("before: " + taskQueue.toVector.map { case (timeOpt, p) => timeOpt.map(t => (t + p.intervalMs - now, p)) })
    val ((_, poll), after) = taskQueue.dequeue

    taskQueue = after.enqueue((Some(now), poll)).sortWith {
      case ((None, lpoll), (None, rpoll)) => true
      case ((None, lpoll), (Some(rtime), rpoll)) => true
      case ((Some(ltime), lpoll), (None, rpoll)) => false
      case ((Some(ltime), lpoll), (Some(rtime), rpoll)) =>
        ltime + lpoll.intervalMs < rtime + rpoll.intervalMs
    }

    logger.trace("after: " + taskQueue.toVector.map { case (timeOpt, p) => timeOpt.map(t => (t + p.intervalMs - now, p)) })

    poll
  }

}

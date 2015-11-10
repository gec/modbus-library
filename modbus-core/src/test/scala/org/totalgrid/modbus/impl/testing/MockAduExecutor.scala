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

import org.totalgrid.modbus.impl._
import net.agileautomata.executor4s.{ Settable, Future }
import net.agileautomata.executor4s.testing.MockFuture

class MockAduExecutor extends AduExecutor {

  private val requests = collection.mutable.Queue.empty[Settable[RequestResponse]]

  def isIdle = requests.isEmpty

  def execute(adu: AduHandler): Future[RequestResponse] = {
    val f = MockFuture.undefined[RequestResponse]
    requests.enqueue(f)
    f
  }

  def set(rsp: RequestResponse) = {
    if (requests.isEmpty) throw new Exception("Expected an outstanding request, but it was empty")
    else requests.dequeue().set(rsp)
  }

}
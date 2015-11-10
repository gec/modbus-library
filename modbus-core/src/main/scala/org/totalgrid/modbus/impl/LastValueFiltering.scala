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

import collection.mutable.Map
import org.totalgrid.modbus.{ ModbusData, ModbusRegister, ModbusBit, ModbusDeviceObserver }

/**
 * Caches values and filters out repeat changes
 */
trait LastValueFiltering extends ModbusDeviceObserver {

  private var commStatus = false

  private val di = Map.empty[Int, ModbusBit]
  private val cs = Map.empty[Int, ModbusBit]
  private val hr = Map.empty[Int, ModbusRegister]
  private val ir = Map.empty[Int, ModbusRegister]

  abstract override def onReadDiscreteInput(list: Traversable[ModbusBit]): Unit = {
    val f = filter(list, di)
    if (!f.isEmpty) super.onReadDiscreteInput(f)
  }

  abstract override def onReadCoilStatus(list: Traversable[ModbusBit]): Unit = {
    val f = filter(list, cs)
    if (!f.isEmpty) super.onReadCoilStatus(f)
  }

  abstract override def onReadHoldingRegister(list: Traversable[ModbusRegister]): Unit = {
    val f = filter(list, hr)
    if (!f.isEmpty) super.onReadHoldingRegister(f)
  }

  abstract override def onReadInputRegister(list: Traversable[ModbusRegister]): Unit = {
    val f = filter(list, ir)
    if (!f.isEmpty) super.onReadInputRegister(f)
  }

  private def filter[A <: ModbusData[_]](list: Traversable[A], map: Map[Int, A]): Traversable[A] = {
    list.filter { b =>
      map.get(b.index) match {
        case Some(x) => {
          if (x.value == b.value) false
          else {
            map.put(b.index, b)
            true
          }
        }
        case None =>
          map.put(b.index, b)
          true
      }
    }
  }

  abstract override def onCommSuccess() = if (commStatus == false) {
    commStatus = true
    super.onCommSuccess()
  }

  abstract override def onCommFailure() = if (commStatus == true) {
    List(di, cs, hr, ir).foreach(_.clear())
    commStatus = false
    super.onCommFailure()
  }
}
package org.totalgrid.modbus.impl.requests
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
import annotation.tailrec

object BitReader {
  def apply[A](start: Int, cnt: Int, offset: Int, array: Array[Byte])(convert: (Int, Boolean) => A): List[A] = {

    val t = new Traversable[A] {
      def foreach[U](fun: A => U): Unit = {
        @tailrec
        def next(i: Int): Unit = if (i < cnt) {
          val byte = i / 8
          val bit = i % 8
          val bool = ((array(byte + offset) >> bit) & 0x01) != 0
          fun(convert(start + i, bool))
          next(i + 1)
        }
        next(0)
      }
    }

    t.toList
  }
}


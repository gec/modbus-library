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
package org.totalgrid.modbus.data

import java.nio.ByteBuffer

object Hex {

  def fromHex(hex: String): Array[Byte] = {
    val cleanInput = hex.replaceAll("\\s|\\n", "")
    if ((cleanInput.length % 2) != 0) throw new IllegalArgumentException("Hex strings must have even number of characters")
    cleanInput.sliding(2, 2).map(s => java.lang.Integer.parseInt(s, 16).toByte).toArray
  }

  def bufferFromHex(hex: String): ByteBuffer = {
    ByteBuffer.wrap(fromHex(hex))
  }

  def toHex(byte: Byte): String = String.format("%02X", java.lang.Byte.valueOf(byte))

  def toHex(bytes: Array[Byte]): String = bytes.map(toHex).mkString(" ")

  def toHex(v: Short): String = {
    val ar = new Array[Byte](2)
    val bb = ByteBuffer.wrap(ar)
    UInt16.writeShort(bb, v)
    toHex(ar)
  }
}

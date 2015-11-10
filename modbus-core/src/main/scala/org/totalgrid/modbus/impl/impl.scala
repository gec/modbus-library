package org.totalgrid.modbus

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
import annotation.tailrec

package object impl {

  implicit def convertByteBufferToRichByteBuffer(buff: ByteBuffer): RichByteBuffer = new RichByteBuffer(buff)

  implicit def convertByteSerializbleToBytes(bs: ByteSerializable): Array[Byte] = bs.toBytes

  implicit def convertByteToByteArray(byte: Byte): Array[Byte] = Array(byte)

  implicit def convertShortToBytes(i: Short): Array[Byte] = Array(((i >> 8) & 0xFF).toByte, (i & 0xFF).toByte)

  implicit def convertArrayToByteBuffer(arr: Array[Byte]) = ByteBuffer.wrap(arr)

  implicit def convertStringToByteBuffer(hex: String) = ByteBuffer.wrap(fromHex(hex))

  implicit def convertStringToByteArray(hex: String) = fromHex(hex)

  implicit def convertByteBufferToBytes(b: ByteBuffer): Array[Byte] = {
    val arr = new Array[Byte](b.remaining())
    @tailrec
    def copy(i: Int): Unit = if (i != arr.size) {
      arr(i) = b.get(i)
      copy(i + 1)
    }
    copy(0)
    arr
  }

  def assemble(bytes: Array[Byte]*) = bytes.foldLeft(Array[Byte]())(_ ++ _)

  def toHex(byte: Byte) = String.format("%02X", java.lang.Byte.valueOf(byte))

  def toHex(bytes: Array[Byte]): String = bytes.map(toHex).mkString(" ")

  def fromHex(hex: String): Array[Byte] = {
    val cleanInput = hex.replaceAll("\\s|\\n", "")
    assert((cleanInput.length % 2) == 0)
    cleanInput.sliding(2, 2).map(s => java.lang.Integer.parseInt(s, 16).toByte).toArray
  }

}
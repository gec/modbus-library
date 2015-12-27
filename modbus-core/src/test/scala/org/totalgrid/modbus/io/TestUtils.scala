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

import java.io.{ InputStream, OutputStream }
import java.net.{ InetSocketAddress, InetAddress, Socket, ServerSocket }
import java.nio.ByteBuffer

import com.typesafe.scalalogging.slf4j.Logging

import scala.concurrent._

class SyncServer(port: Int) extends Logging {
  private val serverSocket = new ServerSocket(port, 1)

  private var clientSocketOpt = Option.empty[Socket]
  private var inputOpt = Option.empty[InputStream]
  private var outputOpt = Option.empty[OutputStream]

  private val mutex = new Object

  def accept(): Unit = {
    mutex.synchronized {
      val clientSocket = serverSocket.accept()
      clientSocketOpt = Some(clientSocket)
      logger.debug("got client socket")

      val input = clientSocket.getInputStream()
      val output = clientSocket.getOutputStream()
      inputOpt = Some(input)
      outputOpt = Some(output)
    }
  }

  def read(buffer: Array[Byte]): Int = {
    mutex.synchronized {
      inputOpt.get.read(buffer)
    }
  }

  def write(data: Array[Byte]): Unit = {
    mutex.synchronized {
      outputOpt.foreach { output =>
        output.write(data, 0, data.length)
        output.flush()
      }
    }
  }

  def close(): Unit = {
    mutex.synchronized {
      serverSocket.close()
      clientSocketOpt.foreach(_.close())
    }
  }
}

class EchoServer(port: Int) extends Logging {
  private val serverSocket = new ServerSocket(port)
  private var clientSocketOpt = Option.empty[Socket]
  private var outputOpt = Option.empty[OutputStream]

  private val mutex = new Object

  def write(data: Array[Byte]): Unit = {
    mutex.synchronized {
      outputOpt.foreach { output =>
        output.write(data, 0, data.length)
        output.flush()
      }
    }
  }

  def run(): Unit = {
    val clientSocket = serverSocket.accept()
    clientSocketOpt = Some(clientSocket)
    logger.debug("got client socket")

    val input = clientSocket.getInputStream()
    val output = clientSocket.getOutputStream()
    mutex.synchronized {
      outputOpt = Some(output)
    }

    var close = false
    val buffer = Array.ofDim[Byte](1024)
    while (!close) {
      try {
        val numRead = input.read(buffer)
        logger.debug("echoer numRead: " + numRead)
        if (numRead < 0) {
          close = true
        } else {
          mutex.synchronized {
            output.write(buffer, 0, numRead)
            output.flush()
          }
        }
      } catch {
        case ex: Throwable =>
          close = true
      }
    }
  }

  def close(): Unit = {
    mutex.synchronized {
      serverSocket.close()
      clientSocketOpt.foreach(_.close())
    }
  }
}

class PromiseChannelUser extends ChannelUser {
  private val mutex = new Object
  private var dataPromise = Option.empty[(Int, Promise[Seq[Array[Byte]]])]
  private var closePromise = Option.empty[Promise[Boolean]]
  private var gotClosed = false
  private var data = Vector.empty[Array[Byte]]

  def dataFuture(amount: Int): Future[Seq[Array[Byte]]] = {
    mutex.synchronized {
      val totalSize = data.foldLeft(0) { case (sum, v) => sum + v.length }
      if (totalSize >= amount) {
        Future.successful(data)
      } else {
        val prom = promise[Seq[Array[Byte]]]
        dataPromise = Some((amount, prom))
        prom.future
      }
    }
  }

  def closeFuture(): Future[Boolean] = {
    mutex.synchronized {
      if (gotClosed) {
        Future.successful(true)
      } else {
        val prom = promise[Boolean]
        closePromise = Some(prom)
        prom.future
      }
    }
  }

  def onData(buffer: ByteBuffer): Unit = {
    mutex.synchronized {
      val ar = new Array[Byte](buffer.remaining())
      buffer.get(ar)
      data = data ++ Vector(ar)
      dataPromise.foreach {
        case (limit, prom) =>
          val totalSize = data.foldLeft(0) { case (sum, v) => sum + v.length }
          if (totalSize >= limit) {
            val promiseResult = data
            data = Vector()
            dataPromise = None
            prom.success(promiseResult)
          }
      }
    }
  }

  def onClosed(): Unit = {
    mutex.synchronized {
      if (!gotClosed) {
        gotClosed = true
        closePromise.foreach(_.success(true))
      } else {
        println("DOUBLE ON CLOSED")
      }
    }
  }
}
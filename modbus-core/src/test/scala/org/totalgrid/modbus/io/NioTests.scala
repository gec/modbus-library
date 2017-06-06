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

import java.io.IOException
import java.net.{ ConnectException, ServerSocket, Socket }
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ Executors, TimeoutException }

import com.typesafe.scalalogging.LazyLogging
import org.junit.runner.RunWith
import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.junit.JUnitRunner

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

@RunWith(classOf[JUnitRunner])
class NioTests extends FunSuite with Matchers with LazyLogging {

  logger.info("Running tests...")

  val selectorCount = new AtomicInteger(0)

  private def combineIntoString(arraySeq: Seq[Array[Byte]]): String = {
    val joined = arraySeq.reduceLeft(Array.concat(_, _))
    new String(joined, "UTF-8")
  }

  def echoerAndThread(port: Int): (EchoServer, Thread) = {
    val echoer = new EchoServer(port)
    val echoThread = new Thread(new Runnable {
      override def run(): Unit = echoer.run()
    }, s"echo thread $port")
    echoThread.start()

    (echoer, echoThread)
  }

  def selectorAndThread(): (NioSelectorManager, Thread) = {
    val selector = new NioSelectorManager(8192)

    val selectorThread = new Thread(new Runnable {
      def run(): Unit = selector.run()
    }, s"selector thread ${selectorCount.getAndIncrement()}")
    selectorThread.start()

    (selector, selectorThread)
  }

  def simpleSendReceive(connection: NioChannel, promises: PromiseChannelUser): Unit = {

    val sizeFut = promises.dataFuture(10)
    val message = "0123456789"
    connection.send(ByteBuffer.wrap(message.getBytes))

    val result = Await.result(sizeFut, 5000.milliseconds)

    val joined = result.reduceLeft(Array.concat(_, _))

    new String(joined, "UTF-8") should equal(message)
  }

  test("connect send receive close") {

    val (echoer, echoThread) = echoerAndThread(50000)

    val (selector, selectorThread) = selectorAndThread()

    try {

      val promises = new PromiseChannelUser

      val closeFut = promises.closeFuture()

      val connection = new NioChannel(promises, selector.channelService())

      connection.connect("127.0.0.1", 50000, 10000)

      simpleSendReceive(connection, promises)

      connection.close()

      Await.result(closeFut, 5000.milliseconds)

    } finally {

      selector.close()

      selectorThread.join()

      echoer.close()
      echoThread.join()
    }
  }

  test("selector close") {

    val (echoer, echoThread) = echoerAndThread(50000)

    val (selector, selectorThread) = selectorAndThread()

    val promises = new PromiseChannelUser

    val closeFut = promises.closeFuture()

    val connection = new NioChannel(promises, selector.channelService())

    connection.connect("127.0.0.1", 50000, 10000)

    simpleSendReceive(connection, promises)

    selector.close()

    Await.result(closeFut, 5000.milliseconds)

    selectorThread.join()

    intercept[IOException] {
      connection.send(ByteBuffer.wrap("message".getBytes()))
    }

    connection.close()

    echoer.close()
    echoThread.join()
  }

  test("remote close") {

    val (echoer, echoThread) = echoerAndThread(50000)

    val (selector, selectorThread) = selectorAndThread()

    val promises = new PromiseChannelUser

    val closeFut = promises.closeFuture()

    val connection = new NioChannel(promises, selector.channelService())

    connection.connect("127.0.0.1", 50000, 10000)

    simpleSendReceive(connection, promises)

    echoer.close()

    Await.result(closeFut, 5000.milliseconds)

    selector.close()
    connection.close()

    echoThread.join()
    selectorThread.join()
  }

  test("async data arrives") {

    val (echoer, echoThread) = echoerAndThread(50000)

    val (selector, selectorThread) = selectorAndThread()

    try {

      val promises = new PromiseChannelUser

      val closeFut = promises.closeFuture()

      val connection = new NioChannel(promises, selector.channelService())

      connection.connect("127.0.0.1", 50000, 10000)

      simpleSendReceive(connection, promises)

      val asyncDataFut = promises.dataFuture(5)

      echoer.write("54321".getBytes())

      val asyncData = Await.result(asyncDataFut, 5000.milliseconds)

      combineIntoString(asyncData) should equal("54321")

      connection.close()

      Await.result(closeFut, 5000.milliseconds)

    } finally {

      selector.close()

      selectorThread.join()

      echoer.close()
      echoThread.join()
    }
  }

  test("simultaneous") {

    val (echoer1, echoThread1) = echoerAndThread(50000)
    val (echoer2, echoThread2) = echoerAndThread(50001)

    val (selector, selectorThread) = selectorAndThread()

    val promises1 = new PromiseChannelUser
    val promises2 = new PromiseChannelUser

    val closeFut1 = promises1.closeFuture()
    val closeFut2 = promises2.closeFuture()

    val dataFut1 = promises1.dataFuture(10 * 10)
    val dataFut2 = promises2.dataFuture(10 * 10)

    val connection1 = new NioChannel(promises1, selector.channelService())
    val connection2 = new NioChannel(promises2, selector.channelService())

    def strChunk(c: Int, i: Int) = s"$c-$i-stuff;"
    def strCombined(c: Int, count: Int) = Range(0, count).map(strChunk(c, _)).mkString("")

    val runner1 = future {
      connection1.connect("127.0.0.1", 50000, 10000)
      Range(0, 10).foreach { i =>
        connection1.send(ByteBuffer.wrap(s"1-$i-stuff;".getBytes))
        Thread.sleep(17)
      }
      connection1.close()
    }

    val runner2 = future {
      connection2.connect("127.0.0.1", 50001, 10000)
      Range(0, 10).foreach { i =>
        connection2.send(ByteBuffer.wrap(s"2-$i-stuff;".getBytes))
        Thread.sleep(13)
      }
      connection2.close()
    }

    try {
      val data1 = Await.result(dataFut1, 5000.milliseconds)
      val data2 = Await.result(dataFut2, 5000.milliseconds)

      combineIntoString(data1) should equal(strCombined(1, 10))
      combineIntoString(data2) should equal(strCombined(2, 10))

      Await.result(closeFut1, 5000.milliseconds)
      Await.result(closeFut2, 5000.milliseconds)

    } finally {
      selector.close()
      selectorThread.join()
      echoer1.close()
      echoer2.close()
    }
  }

  test("fail connect") {
    val (selector, selectorThread) = selectorAndThread()

    val promises = new PromiseChannelUser

    val sizeFut = promises.dataFuture(10)
    val closeFut = promises.closeFuture()

    val connection = new NioChannel(promises, selector.channelService())

    intercept[ConnectException] {
      connection.connect("127.0.0.1", 50000, 10000)
    }

    intercept[IOException] {
      connection.send(ByteBuffer.wrap("test".getBytes))
    }

    // does not throw
    connection.close()

    selector.close()
    selectorThread.join()
  }

  test("connect timeout") {

    val (selector, selectorThread) = selectorAndThread()

    val promises = new PromiseChannelUser

    val closeFut = promises.closeFuture()

    val connection = new NioChannel(promises, selector.channelService())

    try {

      intercept[TimeoutException] {
        connection.connect("10.255.255.1", 50004, 1000)
      }

    } finally {
      selector.close()
      selectorThread.join()
    }
  }

  test("close during connect timeout") {

    val (selector, selectorThread) = selectorAndThread()

    val promises = new PromiseChannelUser

    val closeFut = promises.closeFuture()

    val connection = new NioChannel(promises, selector.channelService())

    try {

      val fut = future {
        connection.connect("10.255.255.1", 50004, 10000)
      }

      Thread.sleep(50)

      connection.close()

      intercept[IOException] {
        Await.result(fut, 20000.milliseconds)
      }

    } finally {
      selector.close()
      selectorThread.join()
    }
  }

  test("selector close during connect timeout") {

    val (selector, selectorThread) = selectorAndThread()

    val promises = new PromiseChannelUser

    val closeFut = promises.closeFuture()

    val connection = new NioChannel(promises, selector.channelService())

    try {

      val fut = future {
        connection.connect("10.255.255.1", 50004, 10000)
      }

      Thread.sleep(50)

      selector.close()

      intercept[IOException] {
        Await.result(fut, 20000.milliseconds)
      }

    } finally {
      selectorThread.join()
    }
  }

  test("write blocked and then unblocked") {

    val syncServer = new SyncServer(50004)

    val (selector, selectorThread) = selectorAndThread()

    val promises = new PromiseChannelUser

    val closeFut = promises.closeFuture()

    val connection = new NioChannel(promises, selector.channelService())

    try {

      val connectFut = future {
        Thread.sleep(50)
        connection.connect("127.0.0.1", 50004, 10000)
      }

      syncServer.accept()

      Await.result(connectFut, 5000.milliseconds)

      var totalWritten = 0
      var buffers = Vector.empty[Array[Byte]]
      var readBuffers = Vector.empty[Array[Byte]]
      val rand = new Random()
      var keepGoing = true
      val amount = 4096 * 100

      // Fill up the buffers until the OS refuses to go further,
      // then read to flush them, check the send completes
      while (keepGoing) {
        val data = Array.ofDim[Byte](amount)
        rand.nextBytes(data)
        buffers = buffers ++ Vector(data)

        totalWritten += amount
        val sendFut = future {
          connection.send(ByteBuffer.wrap(data))
        }
        val completed = try {
          Await.ready(sendFut, 150.milliseconds)
          true
        } catch {
          case ex: TimeoutException =>
            false
        }

        if (!completed) {
          var totalRead = 0
          while (totalRead < totalWritten) {
            val readBuffer = Array.ofDim[Byte](amount)
            val numRead = syncServer.read(readBuffer)
            readBuffers ++= Vector(readBuffer.take(numRead))
            totalRead += numRead
          }

          Await.result(sendFut, 5000.milliseconds)

          keepGoing = false
        }
      }

      val bigSendArray = buffers.reduceLeft(Array.concat(_, _))
      val bigReadArray = readBuffers.reduceLeft(Array.concat(_, _))
      bigSendArray.length should equal(bigReadArray.length)
      util.Arrays.equals(bigSendArray, bigReadArray) should equal(true)

    } finally {

      selector.close()
      selectorThread.join()
    }
  }

}

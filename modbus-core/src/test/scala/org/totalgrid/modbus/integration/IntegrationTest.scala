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
package org.totalgrid.modbus.integration

import com.typesafe.scalalogging.slf4j.Logging
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{ BeforeAndAfterEach, FunSuite }
import org.totalgrid.modbus._
import org.totalgrid.modbus.data.UInt16
import org.totalgrid.modbus.poll.{ Poll, ReadCoilStatus, ReadHoldingRegister, ReadInputRegister }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future, Promise, promise }

@RunWith(classOf[JUnitRunner])
class IntegrationTest extends FunSuite with ShouldMatchers with BeforeAndAfterEach with Logging {

  var r: TestRig = null

  override protected def beforeEach(): Unit = {
    r = new TestRig
  }

  override protected def afterEach(): Unit = {
    r.close()
  }

  class TestRig {
    private val mgr = ModbusManager.start(4096, 6)
    private var slaves = Vector.empty[ModbusSlave]
    private var masters = Vector.empty[ModbusMaster]

    def buildSlave(port: Int, unitIdentifier: Int): ModbusSlave = {
      val slave = new ModbusSlave(34001, 33)
      slaves ++= Vector(slave)
      slave
    }

    def buildMaster(polls: Seq[Poll], port: Int, unitIdentifier: Int, watcher: Watcher) = {
      val master = mgr.addTcpMaster("127.0.0.1", 34001, 33, watcher, watcher, polls, 500, 100, 100)
      masters ++= Vector(master)
      master
    }

    def close(): Unit = {
      slaves.foreach(_.close())
      masters.foreach(_.close())
      mgr.shutdown()
    }
  }

  class SeqWatcher[A](mutex: Object) {
    private var updates = Vector.empty[Seq[A]]
    private var promiseOpt = Option.empty[(Seq[A] => Boolean, Promise[Seq[Seq[A]]])]

    def register(f: Seq[A] => Boolean): Future[Seq[Seq[A]]] = {
      mutex.synchronized {
        val prom = promise[Seq[Seq[A]]]
        promiseOpt = Some((f, prom))
        prom.future
      }
    }

    def update(v: Seq[A]): Unit = {
      mutex.synchronized {
        updates ++= Vector(v)
        promiseOpt match {
          case None =>
          case Some((check, prom)) =>
            if (check(v)) {
              prom.success(updates)
              promiseOpt = None
            }
        }
      }
    }

    def current(): Seq[Seq[A]] = {
      mutex.synchronized(updates)
    }
  }

  class SignalWatcher(mutex: Object) {
    private var signaledCount = 0
    private var promiseOpt = Option.empty[(Int, Promise[Int])]

    def signal(): Unit = {
      mutex.synchronized {
        signaledCount += 1
        promiseOpt.foreach {
          case (requestCount, prom) =>
            if (signaledCount >= requestCount) {
              prom.success(signaledCount)
              promiseOpt = None
            }
        }
      }
    }

    def register(count: Int): Future[Int] = {
      mutex.synchronized {
        if (signaledCount >= count) {
          Future.successful(signaledCount)
        } else {
          val prom = promise[Int]
          promiseOpt = Some((count, prom))
          prom.future
        }
      }
    }

    def count(): Int = {
      mutex.synchronized {
        signaledCount
      }
    }
  }

  class Watcher extends ModbusDeviceObserver with ChannelObserver {
    private val mutex = new Object

    var discreteInputs = new SeqWatcher[ModbusBit](mutex)
    var coilStatuses = new SeqWatcher[ModbusBit](mutex)
    var inputRegisters = new SeqWatcher[ModbusRegister](mutex)
    var holdingRegisters = new SeqWatcher[ModbusRegister](mutex)

    val channelOnline = new SignalWatcher(mutex)
    val channelOpening = new SignalWatcher(mutex)
    val channelOffline = new SignalWatcher(mutex)

    override def onReadDiscreteInput(list: Traversable[ModbusBit]): Unit = {
      discreteInputs.update(list.toSeq)
    }

    override def onReadCoilStatus(list: Traversable[ModbusBit]): Unit = {
      coilStatuses.update(list.toSeq)
    }

    override def onReadHoldingRegister(list: Traversable[ModbusRegister]): Unit = {
      holdingRegisters.update(list.toSeq)
    }

    override def onReadInputRegister(list: Traversable[ModbusRegister]): Unit = {
      inputRegisters.update(list.toSeq)
    }

    override def onChannelOnline(): Unit = {
      channelOnline.signal()
    }

    override def onChannelOffline(): Unit = {
      channelOffline.signal()
    }

    override def onChannelOpening(): Unit = {
      channelOpening.signal()
    }
  }

  test("come up, poll, disconnect") {

    val watcher = new Watcher
    val openingFut = watcher.channelOpening.register(1)
    val onlineFut = watcher.channelOnline.register(1)
    val offlineFut = watcher.channelOffline.register(1)

    val gotUpdate = watcher.inputRegisters.register(_ => true)

    val slave = r.buildSlave(34001, 33)

    slave.setInputRegister(0, 22)
    slave.setInputRegister(1, 11)

    val poll = new ReadInputRegister(UInt16(0), UInt16(2), 500, 1000)

    val master = r.buildMaster(Seq(poll), 34001, 33, watcher)

    Await.result(openingFut, 5000.milliseconds)

    Await.result(onlineFut, 5000.milliseconds)

    val registers = Await.result(gotUpdate, 5000.milliseconds)

    registers.size should equal(1)
    val regPoll = registers.head

    regPoll.size should equal(2)
    regPoll(0).index should equal(0)
    regPoll(0).value.uInt16 should equal(22)
    regPoll(1).index should equal(1)
    regPoll(1).value.uInt16 should equal(11)

    master.close()

    Await.result(offlineFut, 5000.milliseconds)
  }

  test("slave comes up later") {

    val watcher = new Watcher
    val openingFut = watcher.channelOpening.register(2)
    val onlineFut = watcher.channelOnline.register(1)
    val offlineFut = watcher.channelOffline.register(1)

    val master = r.buildMaster(Seq(), 34001, 33, watcher)

    Await.result(openingFut, 6000.milliseconds)
    Await.result(offlineFut, 6000.milliseconds)

    val slave = r.buildSlave(34001, 33)

    Await.result(onlineFut, 6000.milliseconds)

    watcher.channelOpening.count() >= 2 should equal(true)
    watcher.channelOffline.count() >= 1 should equal(true)
    watcher.channelOnline.count() should equal(1)
  }

  test("slave goes down and comes up") {

    val slave = r.buildSlave(34001, 33)

    val watcher = new Watcher
    val openingFut = watcher.channelOpening.register(1)
    val onlineFut = watcher.channelOnline.register(1)
    //val offlineFut = watcher.channelOffline.register(1)

    val poll = new ReadInputRegister(UInt16(0), UInt16(2), 500, 1000)
    val master = r.buildMaster(Seq(poll), 34001, 33, watcher)

    Await.result(openingFut, 6000.milliseconds)
    Await.result(onlineFut, 6000.milliseconds)

    watcher.channelOnline.count() should equal(1)
    val offlineCount = watcher.channelOffline.count()
    val openingCount = watcher.channelOpening.count()
    openingCount should equal(offlineCount + 1)

    val offlineFut = watcher.channelOffline.register(offlineCount + 1)

    slave.close()

    Await.result(offlineFut, 5000.milliseconds)

    val secondOnlineFut = watcher.channelOnline.register(2)

    val slave2 = r.buildSlave(34001, 33)

    Await.result(secondOnlineFut, 5000.milliseconds)
  }

  def regsToSet(seq: Seq[ModbusRegister]): Set[(Int, Int)] = seq.map(r => (r.i, r.value.uInt16)).toSet
  def bitsToSet(seq: Seq[ModbusBit]): Set[(Int, Boolean)] = seq.map(r => (r.i, r.value)).toSet

  test("polls survive connection absence") {

    val slave = r.buildSlave(34001, 33)

    val watcher = new Watcher
    val openingFut = watcher.channelOpening.register(1)
    val onlineFut = watcher.channelOnline.register(1)

    slave.setInputRegister(0, 44)
    slave.setInputRegister(1, 55)

    val firstValues = watcher.inputRegisters.register(regs => regsToSet(regs) == Set((0, 44), (1, 55)))

    val pollIntervalMs = 100
    val poll = new ReadInputRegister(UInt16(0), UInt16(2), pollIntervalMs, 1000)
    val master = r.buildMaster(Seq(poll), 34001, 33, watcher)

    Await.result(openingFut, 6000.milliseconds)
    Await.result(onlineFut, 6000.milliseconds)

    watcher.channelOnline.count() should equal(1)
    val offlineCount = watcher.channelOffline.count()
    val openingCount = watcher.channelOpening.count()
    openingCount should equal(offlineCount + 1)

    Await.result(firstValues, 5000.milliseconds)

    val offlineFut = watcher.channelOffline.register(offlineCount + 1)

    slave.close()

    Await.result(offlineFut, 5000.milliseconds)

    val prevUpdates = watcher.inputRegisters.current()
    val prevPollCount = prevUpdates.size

    regsToSet(prevUpdates.last) should equal(Set((0, 44), (1, 55)))

    val secondOnlineFut = watcher.channelOnline.register(2)

    Thread.sleep((pollIntervalMs * 2.5).toLong)

    val slave2 = r.buildSlave(34001, 33)
    slave2.setInputRegister(0, 66)
    slave2.setInputRegister(1, 77)

    val nextValues = watcher.inputRegisters.register(regs => regsToSet(regs) == Set((0, 66), (1, 77)))

    Await.result(secondOnlineFut, 5000.milliseconds)

    Await.result(nextValues, 5000.milliseconds)
  }

  test("read and write coil") {

    val watcher = new Watcher
    val openingFut = watcher.channelOpening.register(1)
    val onlineFut = watcher.channelOnline.register(1)
    val offlineFut = watcher.channelOffline.register(1)

    val gotOriginal = watcher.coilStatuses.register(_ => true)

    val slave = r.buildSlave(34001, 33)

    val poll = new ReadCoilStatus(UInt16(0), UInt16(2), 100, 1000)

    val master = r.buildMaster(Seq(poll), 34001, 33, watcher)

    Await.result(openingFut, 5000.milliseconds)

    Await.result(onlineFut, 5000.milliseconds)

    val originals = Await.result(gotOriginal, 5000.milliseconds)

    originals.size should equal(1)
    val origPoll = originals.head

    bitsToSet(origPoll) should equal(Set((0, false), (1, false)))

    val getUpdate1 = watcher.coilStatuses.register { r => bitsToSet(r) == Set((0, true), (1, false)) }

    val writeFut1 = master.writeSingleCoil(0, true)
    Await.result(writeFut1, 5000.milliseconds)

    Await.result(getUpdate1, 5000.milliseconds)

    val getUpdate2 = watcher.coilStatuses.register { r => bitsToSet(r) == Set((0, true), (1, true)) }

    val writeFut2 = master.writeSingleCoil(1, true)
    Await.result(writeFut2, 5000.milliseconds)

    Await.result(getUpdate2, 5000.milliseconds)

    val getUpdate3 = watcher.coilStatuses.register { r => bitsToSet(r) == Set((0, true), (1, false)) }

    val writeFut3 = master.writeSingleCoil(1, false)
    Await.result(writeFut3, 5000.milliseconds)

    Await.result(getUpdate3, 5000.milliseconds)

    val getUpdate4 = watcher.coilStatuses.register { r => bitsToSet(r) == Set((0, false), (1, false)) }

    val writeFut4 = master.writeSingleCoil(0, false)
    Await.result(writeFut4, 5000.milliseconds)

    Await.result(getUpdate4, 5000.milliseconds)
  }

  test("read and write holding register") {

    val watcher = new Watcher
    val openingFut = watcher.channelOpening.register(1)
    val onlineFut = watcher.channelOnline.register(1)
    val offlineFut = watcher.channelOffline.register(1)

    val gotOriginal = watcher.holdingRegisters.register(_ => true)

    val slave = r.buildSlave(34001, 33)

    val poll = new ReadHoldingRegister(UInt16(0), UInt16(2), 100, 1000)

    val master = r.buildMaster(Seq(poll), 34001, 33, watcher)

    Await.result(openingFut, 5000.milliseconds)

    Await.result(onlineFut, 5000.milliseconds)

    val originals = Await.result(gotOriginal, 5000.milliseconds)

    originals.size should equal(1)
    val origPoll = originals.head

    regsToSet(origPoll) should equal(Set((0, 0), (1, 0)))

    val getUpdate1 = watcher.holdingRegisters.register { r => regsToSet(r) == Set((0, 11), (1, 0)) }
    val writeFut1 = master.writeSingleRegister(0, 11)
    Await.result(writeFut1, 5000.milliseconds) should equal(true)
    Await.result(getUpdate1, 5000.milliseconds)

    val getUpdate2 = watcher.holdingRegisters.register { r => regsToSet(r) == Set((0, 11), (1, 22)) }
    val writeFut2 = master.writeSingleRegister(1, 22)
    Await.result(writeFut2, 5000.milliseconds) should equal(true)
    Await.result(getUpdate2, 5000.milliseconds)

    val getUpdate3 = watcher.holdingRegisters.register { r => regsToSet(r) == Set((0, 60001), (1, 22)) }
    val writeFut3 = master.writeSingleRegister(0, 60001)
    Await.result(writeFut3, 5000.milliseconds) should equal(true)
    Await.result(getUpdate3, 5000.milliseconds)

    val getUpdate4 = watcher.holdingRegisters.register { r => regsToSet(r) != Set((0, 60001), (1, 22)) }
    val writeFut4 = master.writeSingleRegister(1, -1234)
    Await.result(writeFut4, 5000.milliseconds) should equal(true)
    val negUpdate = Await.result(getUpdate4, 5000.milliseconds)

    negUpdate.last(0).i should equal(0)
    negUpdate.last(0).value.uInt16 should equal(60001)
    negUpdate.last(1).i should equal(1)
    negUpdate.last(1).value.sInt16 should equal(-1234)

  }

  test("read operation") {

    val watcher = new Watcher
    val openingFut = watcher.channelOpening.register(1)
    val onlineFut = watcher.channelOnline.register(1)
    val offlineFut = watcher.channelOffline.register(1)

    val gotUpdate = watcher.inputRegisters.register(_ => true)

    val slave = r.buildSlave(34001, 33)

    slave.setInputRegister(0, 22)
    slave.setInputRegister(1, 11)

    val master = r.buildMaster(Seq(), 34001, 33, watcher)

    Await.result(openingFut, 5000.milliseconds)

    Await.result(onlineFut, 5000.milliseconds)

    val readFut = master.readInputRegisters(0, 2)

    val readResult = Await.result(readFut, 5000.milliseconds)

    readResult.size should equal(2)
    readResult(0).index should equal(0)
    readResult(0).value.uInt16 should equal(22)
    readResult(1).index should equal(1)
    readResult(1).value.uInt16 should equal(11)

    val registers = Await.result(gotUpdate, 5000.milliseconds)

    registers.size should equal(1)
    val regPoll = registers.head

    regPoll.size should equal(2)
    regPoll(0).index should equal(0)
    regPoll(0).value.uInt16 should equal(22)
    regPoll(1).index should equal(1)
    regPoll(1).value.uInt16 should equal(11)

    master.close()

    Await.result(offlineFut, 5000.milliseconds)
  }
}

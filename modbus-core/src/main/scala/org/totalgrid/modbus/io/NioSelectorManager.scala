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
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{ ClosedSelectorException, Selector, SelectionKey, SocketChannel }
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.slf4j.Logging

import scala.collection.mutable

trait NioSelectorChannelCallbacks {
  def onConnect(key: SelectionKey): Boolean
  def onWriteReady(key: SelectionKey)
  def onData(buffer: ByteBuffer)
  def onClose()
  def reregister(selector: Selector): Option[SelectionKey]
}

trait NioServiceRequests {
  def register(socket: SocketChannel, channelCallbacks: NioSelectorChannelCallbacks)
  def registerForWrite(socket: SocketChannel)
  def unregister(socket: SocketChannel)
}

object NioChannel {

  sealed trait ChannelState
  sealed trait ClosedChannelState extends ChannelState
  sealed abstract class ConnectedState(socket: SocketChannel) extends ChannelState {
    def socketChannel = socket
  }
  case object Uninitialized extends ChannelState
  case class WaitingForConnect(socket: SocketChannel) extends ConnectedState(socket)
  case class ConnectSignaled(socket: SocketChannel) extends ConnectedState(socket)
  case class ConnectError(socket: SocketChannel, ex: Throwable) extends ConnectedState(socket)
  case class Idle(socket: SocketChannel) extends ConnectedState(socket)
  case class WaitingForWrite(socket: SocketChannel, writeBuffer: ByteBuffer) extends ConnectedState(socket)
  case class WriteFinishedSignaled(socket: SocketChannel) extends ConnectedState(socket)
  case class WriteDeferredError(socket: SocketChannel, ex: Throwable) extends ConnectedState(socket)
  case object UserClosed extends ClosedChannelState
  case object SelectorClosed extends ClosedChannelState
  case object FailedClosed extends ClosedChannelState

}

class NioChannel(userCallbacks: ChannelUser, selectorRequests: NioServiceRequests) extends ClientConnection with Logging {
  import NioChannel._

  private val stateMutex = new Object
  private var state: ChannelState = Uninitialized

  private val channelCallbacks = new NioSelectorChannelCallbacks {

    def onConnect(key: SelectionKey): Boolean = {
      // SELECTOR THREAD, holding registry lock
      stateMutex.synchronized {
        state match {
          case WaitingForConnect(socket) => {
            try {
              val worked = socket.finishConnect()
              if (!worked) {
                state = ConnectError(socket, new IOException("Connection wasn't open when signaled"))
                key.cancel()
                stateMutex.notifyAll()
                false
              } else {
                state = ConnectSignaled(socket)
                key.interestOps(SelectionKey.OP_READ)
                stateMutex.notifyAll()
                true
              }
            } catch {
              case ex: Throwable =>
                state = ConnectError(socket, ex)
                key.cancel()
                stateMutex.notifyAll()
                false
            }
          }
          case _ =>
            logger.warn("Selector signaled connect when not waiting for signal")
            key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT)
            false
        }
      }
    }

    def onWriteReady(key: SelectionKey): Unit = {
      // SELECTOR THREAD, holding registry lock
      logger.trace("onWriteReady")
      stateMutex.synchronized {
        state match {
          case WaitingForWrite(socket, buffer) => {
            if (buffer.remaining() > 0) {
              logger.trace("doing deferred write: " + buffer.remaining())
              try {
                socket.write(buffer)
                logger.trace("after deferred write: " + buffer.remaining())
                if (buffer.remaining() == 0) {
                  key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE)
                  state = WriteFinishedSignaled(socket)
                  stateMutex.notifyAll()
                }
              } catch {
                case ex: Throwable =>
                  key.cancel()
                  state = WriteDeferredError(socket, ex)
                  stateMutex.notifyAll()
              }
            } else {
              logger.warn("Had buffer in deferred write queue with 0 remaining")
              key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE)
              state = WriteFinishedSignaled(socket)
              stateMutex.notifyAll()
            }
          }
          case st: ClosedChannelState =>
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE)
          case st =>
            // TODO: HANDLE
            logger.warn("Write ready fired in illegal state: " + st.getClass.getSimpleName)
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE)
        }
      }
    }

    def onData(buffer: ByteBuffer): Unit = {
      // SELECTOR THREAD, holding registry lock
      // must copy buffer before marshalling
      buffer.flip()
      logger.trace("onData " + buffer.remaining())
      userCallbacks.onData(buffer.asReadOnlyBuffer())
    }

    def onClose(): Unit = {
      // SELECTOR THREAD, holding registry lock
      stateMutex.synchronized {

        state match {
          case st: ClosedChannelState =>
          case st: ConnectedState =>
            state = SelectorClosed
            try {
              st.socketChannel.close()
            } catch {
              case ex: Throwable =>
                logger.warn("Exception on socket closed: " + ex)
            }
            stateMutex.notifyAll()
          case _ =>
            state = SelectorClosed
            stateMutex.notifyAll()
        }
      }
      userCallbacks.onClosed()
    }

    def reregister(selector: Selector): Option[SelectionKey] = {

      def withKey(socket: SocketChannel, key: Int): Option[SelectionKey] = Some(socket.register(selector, key, getChannelCallbacks))

      stateMutex.synchronized {
        state match {
          case WaitingForConnect(socket) => withKey(socket, SelectionKey.OP_CONNECT)
          case ConnectSignaled(socket) => withKey(socket, SelectionKey.OP_READ)
          case Idle(socket) => withKey(socket, SelectionKey.OP_READ)
          case WaitingForWrite(socket, _) => withKey(socket, SelectionKey.OP_WRITE | SelectionKey.OP_READ)
          case WriteFinishedSignaled(socket) => withKey(socket, SelectionKey.OP_READ)
          case _ => None
        }
      }
    }
  }

  private def getChannelCallbacks: NioSelectorChannelCallbacks = channelCallbacks

  def send(buffer: ByteBuffer): Unit = {
    stateMutex.synchronized {
      state match {
        case Idle(socket) => {
          logger.trace("send: " + buffer.remaining())
          socket.write(buffer)
          if (buffer.remaining() > 0) {
            logger.trace("remaining: " + buffer.remaining())
            state = WaitingForWrite(socket, buffer)
            selectorRequests.registerForWrite(socket)

            var exitLoop = false
            while (!exitLoop) {
              stateMutex.wait()
              state match {
                case WriteFinishedSignaled(sock) => {
                  exitLoop = true
                  state = Idle(sock)
                }
                case WriteDeferredError(sock, ex) => {
                  logger.debug("Deferred write error: " + ex)
                  selectorRequests.unregister(sock)
                  throw ex
                }
                case st: ClosedChannelState => {
                  logger.debug("Channel was closed when woken in send")
                  throw new IOException("Channel closed")
                }
                case st =>
                  throw new IllegalStateException("Channel changed to illegal state before send completed: " + st.getClass.getSimpleName)
              }
            }
          }
        }
        case st: ClosedChannelState => {
          throw new IOException("Channel closed")
        }
        case _ =>
          throw new IllegalStateException("Cannot send while not connected or not idle")
      }
    }
  }

  def connect(host: String, port: Int, timeoutMs: Long): Unit = {
    stateMutex.synchronized {
      state match {
        case Uninitialized => {
          val socketChannel = SocketChannel.open()
          socketChannel.configureBlocking(false)

          socketChannel.connect(new InetSocketAddress(host, port))

          selectorRequests.register(socketChannel, channelCallbacks)

          state = WaitingForConnect(socketChannel)

          var exitLoop = false
          val start = System.currentTimeMillis()
          while (!exitLoop) {
            val now = System.currentTimeMillis()
            val timeLeft = (start + timeoutMs) - now
            logger.debug("Connect wait time left: " + timeLeft)
            if (timeLeft <= 0) {
              exitLoop = true
            } else {
              try {
                stateMutex.wait(timeLeft)
                state match {
                  case w: WaitingForConnect =>
                  /*case cs: ConnectSignaled => exitLoop = true
                  case ce: ConnectError => exitLoop = true*/
                  case _ => exitLoop = true
                }
              } catch {
                case ex: InterruptedException =>
                  exitLoop = true
              }
            }
          }
          state match {
            case ConnectSignaled(_) => {
              logger.debug("Connected")
              state = Idle(socketChannel)
            }
            case ConnectError(_, ex) => {
              logger.debug("Connection failed: " + ex)
              state = FailedClosed
              throw ex
            }
            case st: ClosedChannelState =>
              selectorRequests.unregister(socketChannel)
              throw new IOException("Connection closed before it was completed")
            case _ =>
              selectorRequests.unregister(socketChannel)
              throw new TimeoutException("Couldn't connect within timeout")
          }
        }
        case st =>
          throw new IllegalStateException("Channel already connected")
      }
    }
  }

  def close(): Unit = {
    logger.debug("User closing channel")
    stateMutex.synchronized {
      state match {
        case st: ClosedChannelState =>
          logger.debug("NioChannel already closed")
        case Uninitialized => {
          state = UserClosed
          userCallbacks.onClosed()
        }
        case st: ConnectedState => {
          try {
            st.socketChannel.close()
          } catch {
            case ex: Throwable =>
              logger.warn("Exception on socket close: " + ex)
          }
          state = UserClosed
          stateMutex.notifyAll()
          userCallbacks.onClosed()
          selectorRequests.unregister(st.socketChannel)
        }
      }
    }
  }

}

object NioSelectorManager {

  sealed trait SelectorRequest
  case class Registration(channel: SocketChannel, channelCallbacks: NioSelectorChannelCallbacks) extends SelectorRequest
  case class RegisterForWrite(channel: SocketChannel) extends SelectorRequest
  case class Unregister(channel: SocketChannel) extends SelectorRequest
}
class NioSelectorManager(readBufferSize: Int) extends Logging {
  import NioSelectorManager._

  private val exitSignal = new AtomicBoolean(false)
  private val readBuffer = ByteBuffer.allocateDirect(readBufferSize)
  private var selector = Selector.open()

  private val registryMutex = new Object() // SELECTOR THREAD <-> CLOSE SYNC
  private val requestMutex = new Object() // CHANNEL <-> SELECTOR SYNC
  private val requests = new mutable.Queue[SelectorRequest]()
  private var registry = Map.empty[SocketChannel, (SelectionKey, NioSelectorChannelCallbacks)]

  private def enqueueRequest(request: SelectorRequest): Unit = {
    requestMutex.synchronized {
      requests.enqueue(request)
      selector.wakeup()
    }
  }

  private val innerService = new NioServiceRequests {
    override def register(socket: SocketChannel, channelCallbacks: NioSelectorChannelCallbacks): Unit = {
      enqueueRequest(Registration(socket, channelCallbacks))
    }

    override def registerForWrite(socket: SocketChannel): Unit = {
      enqueueRequest(RegisterForWrite(socket))
    }

    override def unregister(socket: SocketChannel): Unit = {
      enqueueRequest(Unregister(socket))
    }
  }

  def channelService(): NioServiceRequests = innerService

  private def handleRequest: SelectorRequest => Unit = {
    case Registration(channel, channelCallbacks) => {
      logger.trace("selector: registering channel")
      val selectionKey = channel.register(selector, SelectionKey.OP_CONNECT, channelCallbacks)
      registry = registry.updated(channel, (selectionKey, channelCallbacks))
    }
    case RegisterForWrite(channel) => {
      logger.debug("selector: RegisterForWrite: ")
      val interestSet = channel.keyFor(selector).interestOps()
      channel.keyFor(selector).interestOps(interestSet | SelectionKey.OP_WRITE)
    }
    case Unregister(channel) => {
      logger.trace("selector: unregistering")
      registry.get(channel) match {
        case None =>
        case Some((selectionKey, callbacks)) => {
          selectionKey.cancel()
          registry -= channel
        }
      }
    }
  }

  private def handleRead(socket: SocketChannel, key: SelectionKey, userCallbacks: NioSelectorChannelCallbacks): Unit = {
    readBuffer.clear()
    try {
      val numRead = socket.read(readBuffer)
      logger.trace("handleRead: " + numRead)
      if (numRead < 0) {
        logger.debug("Socket closed on read")
        registry -= socket
        key.cancel()
        userCallbacks.onClose()
      } else {
        userCallbacks.onData(readBuffer)
      }
    } catch {
      case ex: IOException =>
        logger.warn("Exception on read: " + ex)
        registry -= socket
        key.cancel()
        userCallbacks.onClose()
    }
  }

  private def reinitialize(): Unit = {
    logger.warn("Selector reinitializing due to repeatedly waking up immediately with no selected keys")
    selector.close()
    selector = Selector.open()
    registry = registry.flatMap {
      case ((ch, (k, callbacks))) =>
        callbacks.reregister(selector).map { key =>
          (ch, (key, callbacks))
        }
    }
  }

  def run(): Unit = {
    var localExit = false
    var inTime = 0L
    var emptyCount = 0

    while (!localExit) {

      val currentRequests = requestMutex.synchronized {
        val result = requests.toVector
        requests.clear()
        result
      }
      currentRequests.foreach(handleRequest)

      logger.trace("Select in")
      val (selectedCount, selectError) = try {
        inTime = System.currentTimeMillis()
        val result = selector.select()

        // WORKAROUND FOR LINUX SELECT BUG
        if (result == 0) {
          val now = System.currentTimeMillis()
          if (now - inTime < 3) {
            emptyCount += 1
            if (emptyCount > 10) {
              reinitialize()
            }
          } else {
            emptyCount = 0
          }
        } else {
          emptyCount = 0
        }

        (result, false)
      } catch {
        case ex: IOException =>
          logger.warn("Exception in select(): " + ex)
          (0, true)
        case ex: ClosedSelectorException =>
          logger.debug("Closed selector exception")
          (0, true)
      }
      logger.trace("Select out")

      if (!selectError) {
        registry.synchronized {
          if (!exitSignal.get()) {
            val selectedKeys = selector.selectedKeys().iterator()
            while (selectedKeys.hasNext) {
              val key = selectedKeys.next()
              selectedKeys.remove()

              if (key.isValid) {

                def callbackFor(k: SelectionKey): Option[NioSelectorChannelCallbacks] = Option(key.attachment()).map(_.asInstanceOf[NioSelectorChannelCallbacks])

                if (key.isAcceptable) {
                  // not implemented
                } else if (key.isConnectable) {

                  logger.trace("selector: isConnectable")
                  callbackFor(key).foreach { callback =>
                    val isAccepted = callback.onConnect(key)
                    if (!isAccepted) {
                      key.cancel()
                      key.channel() match {
                        case sock: SocketChannel =>
                          registry -= sock
                        case _ =>
                      }
                    }
                  }

                } else if (key.isReadable) {

                  logger.trace("selector: isReadable")
                  key.channel() match {
                    case ch: SocketChannel => {
                      callbackFor(key) match {
                        case None => key.cancel() // prevent this from constantly waking us up
                        case Some(callback) => handleRead(ch, key, callback)
                      }
                    }
                    case _ => key.cancel()
                  }

                } else if (key.isWritable) {

                  logger.trace("selector: isWritable")
                  callbackFor(key) match {
                    case None => key.cancel()
                    case Some(callback) => callback.onWriteReady(key)
                  }
                }
              }
            }

          } else {
            localExit = true
          }
        }
      } else {
        localExit = true
      }
    }
    logger.info("Modbus IO exiting")
  }

  def close(): Unit = {
    // TODO: MOVE THIS INTO MAIN LOOP?
    registryMutex.synchronized {
      exitSignal.set(true)
      registry.foreach {
        case (ch, (key, callbacks)) => callbacks.onClose()
      }
    }
    selector.close()
    selector.wakeup()
  }

}

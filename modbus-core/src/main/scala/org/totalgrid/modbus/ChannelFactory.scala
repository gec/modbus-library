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
package org.totalgrid.modbus

import net.agileautomata.executor4s._
import net.agileautomata.nio4s._
import com.typesafe.scalalogging.slf4j.Logging

private[modbus] trait ChannelFactory {
  def open: Future[Result[Channel]]
  def strand: StrandLifeCycle
}

private[modbus] object ChannelFactory extends Logging {

  def tcpClient(service: IoService, host: String, port: Int) = {

    val s = service.createStrand

    new ChannelFactory {
      def strand = s
      def open = {
        logger.info("Attemping connection to " + host + ":" + port)
        service.createTcpConnector(s).connect(host, port)
      }
      override def toString = host + ":" + port
    }
  }

}

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
package org.totalgrid.modbus.japi;


public class ModbusPoll {
    private final PollType type;
    private final int start;
    private final int count;
    private final long intervalMs;
    private final long timeoutMs;

    public ModbusPoll(PollType type, int start, int count, long intervalMs, long timeoutMs) {
        this.type = type;
        this.start = start;
        this.count = count;
        this.intervalMs = intervalMs;
        this.timeoutMs = timeoutMs;
    }

    public PollType getType() {
        return type;
    }

    public int getStart() {
        return start;
    }

    public int getCount() {
        return count;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public String toString() {
        return "ModbusPoll{" +
                "type=" + type +
                ", start=" + start +
                ", count=" + count +
                ", intervalMs=" + intervalMs +
                ", timeoutMs=" + timeoutMs +
                '}';
    }
}

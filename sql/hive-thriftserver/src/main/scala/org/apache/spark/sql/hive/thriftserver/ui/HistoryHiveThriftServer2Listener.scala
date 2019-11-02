/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.hive.thriftserver.ui

import org.apache.spark.SparkConf
import org.apache.spark.internal.Logging
import org.apache.spark.sql.hive.thriftserver.HiveThriftServer2Listener
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.status.ElementTrackingStore

private[spark] class HistoryHiveThriftServer2Listener()
  extends HiveThriftServer2Listener with Logging {
  logError("I inside here")

  //  private val retainedStatements = conf.get(SQLConf.THRIFTSERVER_UI_STATEMENT_LIMIT)
  //  private val retainedSessions = conf.get(SQLConf.THRIFTSERVER_UI_SESSION_LIMIT)
  //
  //  override def trimExecutionIfNecessary(): Unit = {
  //    if (executionList.size > retainedStatements) {
  //      val toRemove = math.max(retainedStatements / 10, 1)
  //      executionList.filter(_._2.finishTimestamp != 0).take(toRemove).foreach { s =>
  //        executionList.remove(s._1)
  //      }
  //    }
  //  }
  //
  //  override def trimSessionIfNecessary(): Unit = {
  //    if (sessionList.size > retainedSessions) {
  //      val toRemove = math.max(retainedSessions / 10, 1)
  //      sessionList.filter(_._2.finishTimestamp != 0).take(toRemove).foreach { s =>
  //        sessionList.remove(s._1)
  //      }
  //    }
  //
  //  }
  override def toString: String = {
   if (!info.sessionList.isEmpty) {
     return "session list is " + info.sessionList
   }
    "No hope"
  }

}
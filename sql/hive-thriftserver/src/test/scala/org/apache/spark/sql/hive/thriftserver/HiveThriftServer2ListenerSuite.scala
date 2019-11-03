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

package org.apache.spark.sql.hive.thriftserver

import java.util.Properties

import org.mockito.Mockito.{mock, RETURNS_SMART_NULLS}
import org.scalatest.BeforeAndAfter

import org.apache.spark.{SparkConf, SparkContext, SparkFunSuite}
import org.apache.spark.scheduler.SparkListenerJobStart
import org.apache.spark.sql.hive.thriftserver.ui.HiveThriftServer2AppStatusStore
import org.apache.spark.sql.test.SharedSparkSession
import org.apache.spark.status.ElementTrackingStore
import org.apache.spark.util.kvstore.InMemoryStore

class HiveThriftServer2ListenerSuite extends SparkFunSuite
  with BeforeAndAfter with SharedSparkSession {

  private var kvstore: ElementTrackingStore = _

  after {
    if (kvstore != null) {
      kvstore.close()
      kvstore = null
    }
  }

  private def createProperties: Properties = {
    val properties = new Properties()
    properties.setProperty(SparkContext.SPARK_JOB_GROUP_ID, "groupId")
    properties
  }

  private def createStatusStore(): HiveThriftServer2AppStatusStore = {
    kvstore = new ElementTrackingStore(new InMemoryStore, new SparkConf())
    val server = mock(classOf[HiveThriftServer2], RETURNS_SMART_NULLS)
    val listener = new HiveThriftServer2Listener(kvstore, Some(server), Some(sqlContext))
    new HiveThriftServer2AppStatusStore(kvstore, Some(listener))
  }

  test("listener events should store successfully") {
    val statusStore = createStatusStore()
    val listener = statusStore.listener.get

    listener.onSessionCreated(SparkListenerSessionCreated("localhost", "sessionId", "user"))
    listener.onStatementStart(SparkListenerStatementStart("id", "sessionId", "dummy query",
      "groupId", "user"))
    listener.onStatementParsed(SparkListenerStatementParsed("id", "dummy plan"))
    listener.onJobStart(SparkListenerJobStart(
      0,
      System.currentTimeMillis(),
      Nil,
      createProperties))
    listener.onStatementFinish(SparkListenerStatementFinish("id"))
    listener.onOperationClosed(SparkListenerOperationClosed("id"))

    assert(statusStore.getOnlineSessionNum == 1)

    listener.onSessionClosed(SparkListenerSessionClosed("sessionId"))

    assert(statusStore.getOnlineSessionNum == 0)
    assert(statusStore.getExecutionList.size == 1)

    val storeExecData = statusStore.getExecutionList.head

    assert(storeExecData.execId == "id")
    assert(storeExecData.sessionId == "sessionId")
    assert(storeExecData.executePlan == "dummy plan")
    assert(storeExecData.jobId == Seq("0"))
  }
}

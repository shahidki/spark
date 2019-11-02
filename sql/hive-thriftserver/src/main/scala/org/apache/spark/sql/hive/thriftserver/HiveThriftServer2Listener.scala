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

import scala.collection.mutable
import org.apache.spark.SparkContext
import org.apache.spark.internal.Logging
import org.apache.spark.scheduler.{SparkListener, SparkListenerEvent, SparkListenerJobStart}
import org.apache.spark.sql.hive.thriftserver.HiveThriftServer2.{ExecutionState, SessionInfo}

import scala.collection.mutable.ArrayBuffer

// TODO: Support KVStore for HiveThriftServer2Listener
private[thriftserver] trait HiveThriftServer2Listener extends SparkListener with Logging {
   val info = new Info(new mutable.HashMap[String, SessionInfo],
    new mutable.HashMap[String, ExecutionInfo])
  protected val sessionList = info.sessionList
  protected val executionList = info.executionList

  def getOnlineSessionNum: Int = synchronized {
    sessionList.count(_._2.finishTimestamp == 0)
  }

  def isExecutionActive(execInfo: ExecutionInfo): Boolean = {
    !(execInfo.state == ExecutionState.FAILED ||
      execInfo.state == ExecutionState.CANCELED ||
      execInfo.state == ExecutionState.CLOSED)
  }

  /**
    * When an error or a cancellation occurs, we set the finishTimestamp of the statement.
    * Therefore, when we count the number of running statements, we need to exclude errors and
    * cancellations and count all statements that have not been closed so far.
    */
  def getTotalRunning: Int = {
    executionList.count {
      case (_, v) => isExecutionActive(v)
    }
  }

  def getSessionList: Seq[SessionInfo] = synchronized { sessionList.values.toSeq }

  def getSession(sessionId: String): Option[SessionInfo] = {
    sessionList.get(sessionId)
  }

  def getExecutionList: Seq[ExecutionInfo] = synchronized { executionList.values.toSeq }

  override def onJobStart(jobStart: SparkListenerJobStart): Unit = synchronized {
    for {
      props <- Option(jobStart.properties)
      groupId <- Option(props.getProperty(SparkContext.SPARK_JOB_GROUP_ID))
      (_, info1) <- executionList if info1.groupId == groupId
    } {
      info1.jobId += jobStart.jobId.toString
      info1.groupId = groupId
    }
  }

  override def onOtherEvent(event: SparkListenerEvent): Unit = {
    logError("Event is " + event.toString)
    event match {
      case e: SparkListenerSessionCreated => onSessionCreated(e.ip, e.sessionId, e.userName)
      case e: SparkListenerSessionClosed => onSessionClosed(e.sessionId)
      case e: SparkListenerStatementStart => onStatementStart(e.id,
        e.sessionId,
        e.statement,
        e.groupId,
        e.userName)
      case e: SparkListenerStatementParsed => onStatementParsed(e.id, e.executionPlan)
      case e: SparkListenerStatementCanceled => onStatementCanceled(e.id)
      case e: SparkListenerStatementError => onStatementError(e.id, e.errorMsg,
        e.errorTrace)
      case e: SparkListenerStatementFinish => onStatementFinish(e.id)
      case e: SparkListenerOperationClosed => onOperationClosed(e.id)
      case _ => // Ignore
    }
  }

  def onSessionCreated(ip: String, sessionId: String,
                       userName: String = "UNKNOWN"): Unit = {
    synchronized {
      logError("From history reached here")
      val info1 = new SessionInfo(sessionId, System.currentTimeMillis, ip, userName)
      sessionList.put(sessionId, info1)
      logError("from history size is" + info.sessionList.size)
      trimSessionIfNecessary()
    }
  }

  def onSessionClosed(sessionId: String): Unit = synchronized {
    sessionList(sessionId).finishTimestamp = System.currentTimeMillis
    trimSessionIfNecessary()
  }

  def onStatementStart( id: String,
                        sessionId: String,
                        statement: String,
                        groupId: String,
                        userName: String = "UNKNOWN"): Unit = synchronized {
    val info = new ExecutionInfo(statement, sessionId, System.currentTimeMillis, userName)
    info.state = ExecutionState.STARTED
    executionList.put(id, info)
    trimExecutionIfNecessary()
    sessionList(sessionId).totalExecution += 1
    executionList(id).groupId = groupId
  }

  def onStatementParsed(id: String, executionPlan: String): Unit = synchronized {
    executionList(id).executePlan = executionPlan
    executionList(id).state = ExecutionState.COMPILED
  }

  def onStatementCanceled(id: String): Unit = synchronized {
    executionList(id).finishTimestamp = System.currentTimeMillis
    executionList(id).state = ExecutionState.CANCELED
    trimExecutionIfNecessary()
  }

  def onStatementError(id: String, errorMsg: String,
                       errorTrace: String): Unit = synchronized {
    executionList(id).finishTimestamp = System.currentTimeMillis
    executionList(id).detail = errorMsg
    executionList(id).state = ExecutionState.FAILED
    trimExecutionIfNecessary()
  }

  def onStatementFinish(id: String): Unit = synchronized {
    executionList(id).finishTimestamp = System.currentTimeMillis
    executionList(id).state = ExecutionState.FINISHED
    trimExecutionIfNecessary()
  }

  def onOperationClosed(id: String): Unit = synchronized {
    executionList(id).closeTimestamp = System.currentTimeMillis
    executionList(id).state = ExecutionState.CLOSED
  }
  def trimExecutionIfNecessary(): Unit = {

  }

  def trimSessionIfNecessary(): Unit = {

  }

  def postLiveListenerBus(event: SparkListenerEvent): Unit = {

  }

}

class Info (val sessionList: mutable.HashMap[String, SessionInfo],
            val executionList: mutable.HashMap[String, ExecutionInfo])

private[thriftserver] class ExecutionInfo(
                                           val statement: String,
                                           val sessionId: String,
                                           val startTimestamp: Long,
                                           val userName: String) {
  var finishTimestamp: Long = 0L
  var closeTimestamp: Long = 0L
  var executePlan: String = ""
  var detail: String = ""
  var state: ExecutionState.Value = ExecutionState.STARTED
  val jobId: ArrayBuffer[String] = ArrayBuffer[String]()
  var groupId: String = ""
  def totalTime(endTime: Long): Long = {
    if (endTime == 0L) {
      System.currentTimeMillis - startTimestamp
    } else {
      endTime - startTimestamp
    }
  }
}
case class SparkListenerSessionCreated(ip: String, sessionId: String, userName: String)
  extends SparkListenerEvent

case class SparkListenerSessionClosed(sessionId: String) extends SparkListenerEvent

case class SparkListenerStatementStart(
              id: String,
              sessionId: String,
              statement: String,
              groupId: String,
              userName: String = "UNKNOWN") extends SparkListenerEvent

case class SparkListenerStatementParsed(id: String, executionPlan: String)
  extends SparkListenerEvent

case class SparkListenerStatementCanceled(id: String) extends SparkListenerEvent

case class SparkListenerStatementError(id: String, errorMsg: String, errorTrace: String)
  extends SparkListenerEvent

case class SparkListenerStatementFinish(id: String) extends SparkListenerEvent

case class SparkListenerOperationClosed(id: String) extends SparkListenerEvent



/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.usermanagement.scheduler

import org.apache.pekko.actor.ActorSystem
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, ScheduledLockService}
import uk.gov.hmrc.usermanagement.service.DataRefreshService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DataRefreshScheduler @Inject()(
  dataRefreshService  : DataRefreshService,
  configuration       : Configuration,
  mongoLockRepository : MongoLockRepository,
  timestampSupport    : TimestampSupport
)(using
  ActorSystem
, ApplicationLifecycle
, ExecutionContext
) extends SchedulerUtils with Logging:

  private val schedulerConfig =
    SchedulerConfig(configuration, "scheduler.dataRefresh")

  private val dataRefreshLock: ScheduledLockService =
    ScheduledLockService(
      lockRepository    = mongoLockRepository,
      lockId            = "user-management-data-refresh-lock",
      timestampSupport  = timestampSupport,
      schedulerInterval = schedulerConfig.interval
    )

  scheduleWithLock("User Management Data Refresh", schedulerConfig, dataRefreshLock):
    given HeaderCarrier = HeaderCarrier()
    for 
      _ <- Future.successful(logger.info("Beginning user management data refresh"))
      _ <- dataRefreshService.updateUsersAndTeams()
    yield ()

  def manualReload()(using HeaderCarrier): Future[Unit] =
    dataRefreshLock
      .withLock:
        logger.info("Data refresh has been manually triggered")
        dataRefreshService.updateUsersAndTeams()
      .map:
        _.getOrElse(logger.info(s"The Reload process is locked for ${dataRefreshLock.lockId}"))
end DataRefreshScheduler

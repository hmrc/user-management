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
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, ScheduledLockService}
import uk.gov.hmrc.usermanagement.config.SchedulerConfigs
import uk.gov.hmrc.usermanagement.service.DataRefreshService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SlackUsersScheduler @Inject()(
  dataRefreshService  : DataRefreshService,
  config              : SchedulerConfigs,
  mongoLockRepository : MongoLockRepository,
  timestampSupport    : TimestampSupport
)(using
  ActorSystem
, ApplicationLifecycle
, ExecutionContext
) extends SchedulerUtils with Logging:

  private val slackUsersLock: ScheduledLockService =
    ScheduledLockService(
      lockRepository    = mongoLockRepository,
      lockId            = "user-management-slack-users-lock",
      timestampSupport  = timestampSupport,
      schedulerInterval = config.slackUsersScheduler.interval
    )

  scheduleWithLock("User Management Slack Users", config.slackUsersScheduler, slackUsersLock):
    given HeaderCarrier = HeaderCarrier()
    for 
      _ <- Future.successful(logger.info("Beginning user management slack users refresh"))
      _ <- dataRefreshService.updateSlackUsers()
    yield ()

  def manualReload()(using HeaderCarrier): Future[Unit] =
    slackUsersLock
      .withLock:
        logger.info("Slack users refresh has been manually triggered")
        dataRefreshService.updateSlackUsers()
      .map:
        _.getOrElse(logger.info(s"The slack users refresh process is locked for ${slackUsersLock.lockId}"))
end SlackUsersScheduler

/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, ScheduledLockService}
import uk.gov.hmrc.usermanagement.connectors.{TeamsAndRepositoriesConnector, UmpConnector}
import uk.gov.hmrc.usermanagement.service.SlackService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlackChannelScheduler @Inject()(
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  umpConnector                 : UmpConnector,
  slackService                 : SlackService,
  configuration                : Configuration,
  mongoLockRepository          : MongoLockRepository,
  timestampSupport             : TimestampSupport
)(using
  ActorSystem
, ApplicationLifecycle
, ExecutionContext
) extends SchedulerUtils with Logging:

  private val schedulerKey    = "scheduler.slackChannelSync"
  private val testMode        = configuration.get[Boolean]("scheduler.slackChannelSync.testMode")
  private val schedulerConfig = SchedulerConfig(configuration, schedulerKey)

  private val slackChannelLock = ScheduledLockService(
    lockRepository    = mongoLockRepository,
    lockId            = "user-management-slack-channel-lock",
    timestampSupport  = timestampSupport,
    schedulerInterval = schedulerConfig.interval
  )

  scheduleWithLock("User Management Slack Channel", schedulerConfig, slackChannelLock):
    given HeaderCarrier = HeaderCarrier()
    for
      _              <- Future.successful(logger.info("Beginning user management slack channel sync"))
      allTeams       <- umpConnector.getAllTeams()
      githubTeams    <- teamsAndRepositoriesConnector.allTeams()
      teamsToProcess =  allTeams
                          .filter(t => githubTeams.exists(gt => gt.name.asString.equalsIgnoreCase(t.teamName)))
                          .filter(_.slack.isEmpty)
      _              <- Future.successful(logger.info(s"Slack channel sync has found ${teamsToProcess.size} teams without slack channels to process"))
      _              <- slackService.ensureChannelExistsAndSyncMembers(teamsToProcess, testMode)
    yield ()

end SlackChannelScheduler

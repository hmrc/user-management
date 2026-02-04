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

package uk.gov.hmrc.usermanagement.service

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.usermanagement.connectors.{SlackConnector, UmpConnector}
import uk.gov.hmrc.usermanagement.model.{SlackUser, Team, User}
import uk.gov.hmrc.usermanagement.persistence.{SlackUsersRepository, TeamsRepository, UsersRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

@Singleton
class DataRefreshService @Inject()(
  umpConnector   : UmpConnector,
  usersRepository: UsersRepository,
  teamsRepository: TeamsRepository,
  slackRepository: SlackUsersRepository,
  config         : Configuration,
  slackConnector : SlackConnector
)(using
  ExecutionContext
) extends Logging:

  private lazy val umpRequestThrottle: FiniteDuration =
    config.get[FiniteDuration]("ump.requestThrottle")

  def updateUsersAndTeams()(using Materializer, HeaderCarrier): Future[Unit] =
    for
      umpUsers             <- umpConnector.getAllUsers()
      _                    =  logger.info(s"Successfully retrieved ${umpUsers.length} users from UMP")
      usersWithSlack       <- addSlackIdsToUsers(umpUsers)
      umpTeams             <- umpConnector.getAllTeams()
      _                    =  logger.info(s"Successfully retrieved ${umpTeams.length} teams from UMP")
      mergedUsers          =  usersWithSlack.map( user => user.copy(role = Option(user.role).getOrElse("user")))
      _                    =  logger.info(s"Going to insert ${umpTeams.length} teams and ${mergedUsers.length}" +
                                s"human users into their respective repositories")
      _                    <- usersRepository.putAll(mergedUsers)
      _                    =  logger.info("Successfully refreshed users data from UMP.")
      _                    <- teamsRepository.putAll(umpTeams)
      _                    =  logger.info("Successfully refreshed teams data from UMP.")
    yield ()

  def updateSlackUsers()(using Materializer, HeaderCarrier): Future[Unit] =
    for
      slackUsers <- slackConnector.getAllSlackUsers()
      _          <- slackRepository.putAll(slackUsers)
      _          =  logger.info(s"Successfully refreshed slack users, count: ${slackUsers.length}")
    yield ()

  private def addSlackIdsToUsers(umpUsers: Seq[User]): Future[Seq[User]] =
    slackRepository.findAll().map: allSlackUsers =>
      val slackUsersByEmail =
        allSlackUsers
          .collect:
            case user if user.email.isDefined => user.email.get -> user
          .toMap

      val slackUsersByUsername =
        allSlackUsers
          .map(user => user.name -> user)
          .toMap

      umpUsers.map: user =>
        val slackUserOpt =
          slackUsersByEmail.get(user.primaryEmail)
            .orElse(slackUsersByUsername.get(user.username))

        slackUserOpt match
          case Some(slackUser) => user.copy(slackId = Some(slackUser.id))
          case None => user

end DataRefreshService

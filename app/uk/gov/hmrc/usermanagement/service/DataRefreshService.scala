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

import cats.implicits.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.usermanagement.connectors.{SlackConnector, UmpConnector}
import uk.gov.hmrc.usermanagement.model.{Member, SlackUser, Team, User}
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
      usersWithSlack       <- addSlackIDsToUsers(umpUsers)
      umpTeamNames         <- umpConnector.getAllTeams().map(_.map(_.teamName))
      _                    =  logger.info("Successfully retrieved team names from UMP")
      teamsWithMembers     <- getTeamsWithMembers(umpTeamNames)
      _                    =  logger.info("Successfully retrieved all teams with members from UMP")
      usersWithMemberships =  addMembershipsToUsers(usersWithSlack, teamsWithMembers)
      _                    =  logger.info(s"Going to insert ${teamsWithMembers.length} teams and ${usersWithMemberships.length} " +
                                s"human users into their respective repositories")
      _                    <- usersRepository.putAll(usersWithMemberships)
      _                    =  logger.info("Successfully refreshed users data from UMP.")
      _                    <- teamsRepository.putAll(teamsWithMembers)
      _                    =  logger.info("Successfully refreshed teams data from UMP.")
    yield ()

  def updateSlackUsers()(using Materializer, HeaderCarrier): Future[Unit] =
    for
      slackUsers <- slackConnector.getAllSlackUsers()
      _          <- slackRepository.putAll(slackUsers)
      _          =  logger.info(s"Successfully refreshed slack users, count: ${slackUsers.length}")
    yield ()

  private def addSlackIDsToUsers(umpUsers: Seq[User]): Future[Seq[User]] =
    umpUsers.foldLeftM(Seq.empty[User]):
      (acc, user) =>
        slackRepository.findByEmail(user.primaryEmail).map:
          case Some(slackUser) =>
            acc :+ user.copy(slackId = Some(slackUser.id))
          case None =>
            // TODO should we try and match username as a fallback?
            // slackRepository.findByName(user.username)
            acc:+ user

  //Note this step is required, in order to get the roles for each user. This data is not available from the getAllTeams call.
  //This is because GetAllTeams has a bug, in which the `members` field always returns an empty array.
  private def getTeamsWithMembers(teams: Seq[String])(using Materializer, HeaderCarrier): Future[Seq[Team]] =
    Source(teams)
      .throttle(1, umpRequestThrottle)
      .mapAsync(1)(teamName => umpConnector.getTeamWithMembers(teamName))
      .runWith(Sink.collection[Option[Team], Seq[Option[Team]]])
      .map(_.flatten)
  
  private def addMembershipsToUsers(users: Seq[User], teamsWithMembers: Seq[Team]): Seq[User] = 
    val teamAndMembers: Seq[(String, Member)] = teamsWithMembers.flatMap(team => team.members.map(member => team.teamName -> member))
    users.map: user =>
      val membershipsForUser = teamAndMembers.filter(_._2.username == user.username)
      user.copy(
        teamNames = membershipsForUser.map(_._1),
        role      = membershipsForUser.map(_._2).headOption.fold("user")(_.role)
      )
end DataRefreshService

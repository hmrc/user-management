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

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.usermanagement.connectors.{SlackConnector, UmpConnector}
import uk.gov.hmrc.usermanagement.model.{Member, SlackUser, Team, User}
import uk.gov.hmrc.usermanagement.persistence.{TeamsRepository, UsersRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.usermanagement.persistence.SlackUsersRepository

class DataRefreshServiceSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar
    with IntegrationPatience:

  private given HeaderCarrier = HeaderCarrier()
  private given ActorSystem   = ActorSystem("test")

  "updateUsersAndTeams" should:
    "update the Users and Teams repositories based on the data received from UMP" in new Setup:
      when(umpConnector.getAllUsers())
        .thenReturn(Future.successful(Seq(
          User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"),  organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", slackId = None, username = "joe.bloggs", githubUsername = None, phoneNumber = None, role = "user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = false),
          User(displayName = Some("Jane Doe"),   familyName = "Doe",    givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com",   slackId = None, username = "jane.doe",   githubUsername = None, phoneNumber = None, role = "user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = false), //role defaulted to "user" should get updated to admin when refreshed
        )))

      when(umpConnector.getAllTeams())
        .thenReturn(Future.successful(Seq(
          Team(members = Seq.empty, teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None)
        )))

      when(umpConnector.getTeamWithMembers("team1"))
        .thenReturn(Future.successful(Some(
          Team(
            members = Seq(Member("joe.bloggs", Some("Joe Bloggs"), "user", isNonHuman = false)), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None
          )
        )))

      when(umpConnector.getTeamWithMembers("team2"))
        .thenReturn(Future.successful(Some(
          Team(
            members = Seq(Member("jane.doe", Some("Jane Doe"), "team-admin", isNonHuman = false)), teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None
          )
        )))

      when(umpConnector.getTeamWithMembers("team3"))
        .thenReturn(Future.successful(Some(Team(
          members = Seq.empty[Member], teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None
        ))))

      when(slackRepository.findByEmail("jane.doe@gmail.com"))
        .thenReturn(Future.successful(Some(SlackUser(email= Some("jane.doe@gmail.com"), id= "ABCD", name = "jane.doe", isBot = false, isDeleted = false))))

      when(slackRepository.findByEmail("joe.bloggs@gmail.com"))
        .thenReturn(Future.successful(Option.empty[SlackUser]))

      service.updateUsersAndTeams().futureValue

      verify(usersRepository).putAll(Seq(
        User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"),  organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", slackId = None,         username = "joe.bloggs", githubUsername = None, phoneNumber = None, role = "user",       teamNames = Seq("team1"), isDeleted = false, isNonHuman = false),
        User(displayName = Some("Jane Doe"),   familyName = "Doe",    givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com",   slackId = Some("ABCD"), username = "jane.doe",   githubUsername = None, phoneNumber = None, role = "team-admin", teamNames = Seq("team2"), isDeleted = false, isNonHuman = false),
      ))

      verify(teamsRepository).putAll(Seq(
        Team(members = Seq(Member(username = "joe.bloggs", displayName = Some("Joe Bloggs"), role = "user"      , isNonHuman = false)),       teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq(Member(username = "jane.doe"  , displayName = Some("Jane Doe")  , role = "team-admin", isNonHuman = false)), teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None)
      ))

    "Handle users existing in more than one team" in new Setup:
      when(umpConnector.getAllUsers())
        .thenReturn(Future.successful(Seq(
          User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"),  organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", slackId = None, username = "joe.bloggs", githubUsername = None, phoneNumber = None, role = "user",       teamNames = Seq.empty[String], isDeleted = false, isNonHuman = false),
          User(displayName = Some("Jane Doe"),   familyName = "Doe",    givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com",   slackId = None, username = "jane.doe",   githubUsername = None, phoneNumber = None, role = "team-admin", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = false),
        )))

      when(umpConnector.getAllTeams())
        .thenReturn(Future.successful(Seq(
          Team(members = Seq.empty, teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None)
        )))

      when(umpConnector.getTeamWithMembers("team1"))
        .thenReturn(Future.successful(Some(
          Team(
            members = Seq(Member("jane.doe", Some("Jane Doe"), "team-admin", isNonHuman = false), Member("joe.bloggs", Some("Joe Bloggs"), "user", isNonHuman = false)), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None
          )
        )))

      when(umpConnector.getTeamWithMembers("team2"))
        .thenReturn(Future.successful(Some(
          Team(
            members = Seq(Member("jane.doe", Some("Jane Doe"), "team-admin", isNonHuman = false), Member("joe.bloggs", Some("Joe Bloggs"), "user", isNonHuman = false)), teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None
          )
        )))

      when(umpConnector.getTeamWithMembers("team3"))
        .thenReturn(Future.successful(Some(Team(
          members = Seq.empty[Member], teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None
        ))))

      when(slackRepository.findByEmail(any))
        .thenReturn(Future.successful(Option.empty[SlackUser]))

      service.updateUsersAndTeams().futureValue

      verify(usersRepository).putAll(Seq(
        User(
          displayName    = Some("Joe Bloggs"),
          familyName     = "Bloggs",
          givenName      = Some("Joe"),
          organisation   = Some("MDTP"),
          primaryEmail   = "joe.bloggs@gmail.com",
          slackId        = None,
          username       = "joe.bloggs",
          githubUsername = None,
          phoneNumber    = None,
          role           = "user",
          teamNames      = Seq("team1", "team2"),
          isDeleted      = false,
          isNonHuman     = false
        ),
        User(
          displayName    = Some("Jane Doe"),
          familyName     = "Doe",
          givenName      = Some("Jane"),
          organisation   = Some("MDTP"),
          primaryEmail   = "jane.doe@gmail.com",
          slackId        = None,
          username       = "jane.doe",
          githubUsername = None,
          phoneNumber    = None,
          role           = "team-admin",
          teamNames      = Seq("team1", "team2"),
          isDeleted      = false,
          isNonHuman     = false
        ),
      ))

      verify(teamsRepository).putAll(Seq(
        Team(members = Seq(Member(username = "jane.doe", displayName = Some("Jane Doe"), role = "team-admin", isNonHuman = false), Member(username = "joe.bloggs", displayName = Some("Joe Bloggs"), role = "user", isNonHuman = false)), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq(Member(username = "jane.doe", displayName = Some("Jane Doe"), role = "team-admin", isNonHuman = false), Member(username = "joe.bloggs", displayName = Some("Joe Bloggs"), role = "user", isNonHuman = false)), teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None)
      ))
end DataRefreshServiceSpec

trait Setup:
  val umpConnector    = mock[UmpConnector]
  val usersRepository = mock[UsersRepository]
  val teamsRepository = mock[TeamsRepository]
  val slackRepository = mock[SlackUsersRepository]
  val slackConnector  = mock[SlackConnector]
  val config          = Configuration(ConfigFactory.load("application.conf"))

  val service: DataRefreshService = DataRefreshService(umpConnector, usersRepository, teamsRepository, slackRepository, config, slackConnector)

  when(teamsRepository.putAll(any[Seq[Team]]))
    .thenReturn(Future.successful( () ))

  when(usersRepository.putAll(any[Seq[User]]))
    .thenReturn(Future.successful( () ))

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

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{mock, when}
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.usermanagement.connectors.UserManagementConnector
import uk.gov.hmrc.usermanagement.model.{Member, Team, TeamMembership, User}
import uk.gov.hmrc.usermanagement.persistence.{TeamsRepository, UsersRepository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


class DataRefreshServiceSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar
    with IntegrationPatience {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "updateUsersAndTeams" should {
    "update the Users and Teams repositories based on the data received from UMP" in new Setup {
      when(userManagementConnector.getAllUsers())
        .thenReturn(Future.successful(Seq(
          User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, teamsAndRoles = None),
          User(displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, teamsAndRoles = None),
        )))

      when(userManagementConnector.getAllTeams())
        .thenReturn(Future.successful(Seq(
          Team(members = Seq.empty, teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None)
        )))

      when(userManagementConnector.getTeamWithMembers("team1"))
        .thenReturn(Future.successful(Some(
          Team(
            members = Seq(Member("joe.bloggs", Some("Joe Bloggs"), "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None
          )
        )))

      when(userManagementConnector.getTeamWithMembers("team2"))
        .thenReturn(Future.successful(Some(
          Team(
            members = Seq(Member("jane.doe", Some("Jane Doe"), "team-admin")), teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None
          )
        )))

      when(userManagementConnector.getTeamWithMembers("team3"))
        .thenReturn(Future.successful(Some(Team(
          members = Seq.empty[Member], teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None
        ))))

      service.updateUsersAndTeams().futureValue

      verify(usersRepository).putAll(Seq(
        User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, teamsAndRoles = Some(Seq(TeamMembership(teamName = "team1", role = "user")))),
        User(displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, teamsAndRoles = Some(Seq(TeamMembership(teamName = "team2", role = "team-admin")))),
      ))

      verify(teamsRepository).putAll(Seq(
        Team(members = Seq(Member(username = "joe.bloggs", displayName = Some("Joe Bloggs"), role = "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq(Member(username = "jane.doe", displayName = Some("Jane Doe"), role = "team-admin")), teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None)
      ))
    }

    "Handle users existing in more than one team" in new Setup {
      when(userManagementConnector.getAllUsers())
        .thenReturn(Future.successful(Seq(
          User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, teamsAndRoles = None),
          User(displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, teamsAndRoles = None),
        )))

      when(userManagementConnector.getAllTeams())
        .thenReturn(Future.successful(Seq(
          Team(members = Seq.empty, teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None)
        )))

      when(userManagementConnector.getTeamWithMembers("team1"))
        .thenReturn(Future.successful(Some(
          Team(
            members = Seq(Member("jane.doe", Some("Jane Doe"), "team-admin"), Member("joe.bloggs", Some("Joe Bloggs"), "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None
          )
        )))

      when(userManagementConnector.getTeamWithMembers("team2"))
        .thenReturn(Future.successful(Some(
          Team(
            members = Seq(Member("jane.doe", Some("Jane Doe"), "user"), Member("joe.bloggs", Some("Joe Bloggs"), "team-admin")), teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None
          )
        )))

      when(userManagementConnector.getTeamWithMembers("team3"))
        .thenReturn(Future.successful(Some(Team(
          members = Seq.empty[Member], teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None
        ))))

      service.updateUsersAndTeams().futureValue

      verify(usersRepository).putAll(Seq(
        User(
          displayName = Some("Joe Bloggs"),
          familyName = "Bloggs",
          givenName = Some("Joe"),
          organisation = Some("MDTP"),
          primaryEmail = "joe.bloggs@gmail.com",
          username = "joe.bloggs",
          github = None,
          phoneNumber = None,
          teamsAndRoles = Some(Seq(TeamMembership(teamName = "team1", role = "user"), TeamMembership(teamName = "team2", role = "team-admin")))),
        User(
          displayName = Some("Jane Doe"),
          familyName = "Doe",
          givenName = Some("Jane"),
          organisation = Some("MDTP"),
          primaryEmail = "jane.doe@gmail.com",
          username = "jane.doe",
          github = None,
          phoneNumber = None,
          teamsAndRoles = Some(Seq(TeamMembership(teamName = "team1", role = "team-admin"), TeamMembership(teamName = "team2", role = "user")))),
      ))

      verify(teamsRepository).putAll(Seq(
        Team(members = Seq(Member(username = "jane.doe", displayName = Some("Jane Doe"), role = "team-admin"), Member(username = "joe.bloggs", displayName = Some("Joe Bloggs"), role = "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq(Member(username = "jane.doe", displayName = Some("Jane Doe"), role = "user"), Member(username = "joe.bloggs", displayName = Some("Joe Bloggs"), role = "team-admin")), teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None)
      ))
    }
  }
}

trait Setup {
  val userManagementConnector = mock[UserManagementConnector]
  val usersRepository         = mock[UsersRepository]
  val teamsRepository         = mock[TeamsRepository]

  val service = new DataRefreshService(userManagementConnector, usersRepository, teamsRepository)

  when(teamsRepository.putAll(any[Seq[Team]]))
    .thenReturn(Future.successful( () ))

  when(usersRepository.putAll(any[Seq[User]]))
    .thenReturn(Future.successful( () ))
}

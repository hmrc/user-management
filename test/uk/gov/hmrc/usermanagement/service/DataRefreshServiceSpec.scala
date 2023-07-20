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
import uk.gov.hmrc.usermanagement.model.{Member, Team, TeamAndRole, TeamMember, User}
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

      when(userManagementConnector.getMembersForTeam("team1"))
        .thenReturn(Future.successful(Some(Seq(TeamMember(
          displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, role = "user"
        )))))

      when(userManagementConnector.getMembersForTeam("team2"))
        .thenReturn(Future.successful(Some(Seq(TeamMember(
          displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, role = "team-admin"
        )))))

      when(userManagementConnector.getMembersForTeam("team3"))
        .thenReturn(Future.successful(Some(Seq.empty)))

      when(usersRepository.findAllUsernames())
        .thenReturn(Future.successful(Seq.empty))

      when(teamsRepository.findAllTeamNames())
        .thenReturn(Future.successful(Seq.empty))

      service.updateUsersAndTeams().futureValue

      verify(usersRepository).replaceOrInsertMany(Seq(
        User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, teamsAndRoles = Some(Seq(TeamAndRole(teamName = "team1", role = "user")))),
        User(displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, teamsAndRoles = Some(Seq(TeamAndRole(teamName = "team2", role = "team-admin")))),
      ))

      verify(teamsRepository).replaceOrInsertMany(Seq(
        Team(members = Seq(Member(username = "joe.bloggs", role = "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq(Member(username = "jane.doe", role = "team-admin")), teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
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

      when(userManagementConnector.getMembersForTeam("team1"))
        .thenReturn(Future.successful(Some(Seq(
          TeamMember(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, role = "user"),
          TeamMember(displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, role = "team-admin")
        ))))

      when(userManagementConnector.getMembersForTeam("team2"))
        .thenReturn(Future.successful(Some(Seq(
          TeamMember(displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, role = "user"),
          TeamMember(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, role = "team-admin"),
        ))))

      when(userManagementConnector.getMembersForTeam("team3"))
        .thenReturn(Future.successful(Some(Seq.empty)))

      when(usersRepository.findAllUsernames())
        .thenReturn(Future.successful(Seq.empty))

      when(teamsRepository.findAllTeamNames())
        .thenReturn(Future.successful(Seq.empty))

      service.updateUsersAndTeams().futureValue

      verify(usersRepository).replaceOrInsertMany(Seq(
        User(
          displayName = Some("Joe Bloggs"),
          familyName = "Bloggs",
          givenName = Some("Joe"),
          organisation = Some("MDTP"),
          primaryEmail = "joe.bloggs@gmail.com",
          username = "joe.bloggs",
          github = None,
          phoneNumber = None,
          teamsAndRoles = Some(Seq(TeamAndRole(teamName = "team1", role = "user"), TeamAndRole(teamName = "team2", role = "team-admin")))),
        User(
          displayName = Some("Jane Doe"),
          familyName = "Doe",
          givenName = Some("Jane"),
          organisation = Some("MDTP"),
          primaryEmail = "jane.doe@gmail.com",
          username = "jane.doe",
          github = None,
          phoneNumber = None,
          teamsAndRoles = Some(Seq(TeamAndRole(teamName = "team1", role = "team-admin"), TeamAndRole(teamName = "team2", role = "user")))),
      ))

      verify(teamsRepository).replaceOrInsertMany(Seq(
        Team(members = Seq(Member(username = "jane.doe", role = "team-admin"), Member(username = "joe.bloggs", role = "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq(Member(username = "jane.doe", role = "user"), Member(username = "joe.bloggs", role = "team-admin")), teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None)
      ))
    }

    "Discard non-human users during a data refresh " in new Setup {
      when(userManagementConnector.getAllUsers())
        .thenReturn(Future.successful(Seq(
          User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, teamsAndRoles = None),
          User(displayName = None, familyName = "robot1", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "Service_account", github = None, phoneNumber = None, teamsAndRoles = None),
          User(displayName = None, familyName = "robot2", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "PlatOps", github = None, phoneNumber = None, teamsAndRoles = None),
          User(displayName = None, familyName = "robot3", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "Build", github = None, phoneNumber = None, teamsAndRoles = None),
          User(displayName = None, familyName = "robot4", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "DEPLOY", github = None, phoneNumber = None, teamsAndRoles = None),
          User(displayName = None, familyName = "robot5", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "deskPro", github = None, phoneNumber = None, teamsAndRoles = None),
          User(displayName = None, familyName = "robot6", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "DDCOPS", github = None, phoneNumber = None, teamsAndRoles = None),
          User(displayName = None, familyName = "robot7", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "PlatSEC", github = None, phoneNumber = None, teamsAndRoles = None),
        )))

      when(userManagementConnector.getAllTeams())
        .thenReturn(Future.successful(Seq(
          Team(members = Seq.empty, teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        )))

      when(userManagementConnector.getMembersForTeam("team1"))
        .thenReturn(Future.successful(Some(Seq(
          TeamMember(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, role = "user"),
          TeamMember(displayName = None, familyName = "robot1", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "Service_account", github = None, phoneNumber = None, role = "user"),
          TeamMember(displayName = None, familyName = "robot2", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "PlatOps", github = None, phoneNumber = None, role = "team-admin"),
          TeamMember(displayName = None, familyName = "robot3", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "Build", github = None, phoneNumber = None, role = "user"),
          TeamMember(displayName = None, familyName = "robot4", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "DEPLOY", github = None, phoneNumber = None, role = "team-admin"),
          TeamMember(displayName = None, familyName = "robot5", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "deskPro", github = None, phoneNumber = None, role = "user"),
          TeamMember(displayName = None, familyName = "robot6", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "DDCOPS", github = None, phoneNumber = None, role = "team-admin"),
          TeamMember(displayName = None, familyName = "robot7", givenName = None, organisation = None, primaryEmail = "test@gmail.com", username = "PlatSEC", github = None, phoneNumber = None, role = "user")
        ))))

      when(usersRepository.findAllUsernames())
        .thenReturn(Future.successful(Seq.empty))

      when(teamsRepository.findAllTeamNames())
        .thenReturn(Future.successful(Seq.empty))

      service.updateUsersAndTeams().futureValue

      verify(usersRepository).replaceOrInsertMany(Seq(
        User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, teamsAndRoles = Some(Seq(TeamAndRole(teamName = "team1", role = "user")))),
      ))

      verify(teamsRepository).replaceOrInsertMany(Seq(
        Team(members = Seq(Member(username = "joe.bloggs", role = "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
      ))
    }

    "Delete users/teams that are no longer present in UMP" in new Setup {

      when(userManagementConnector.getAllUsers())
        .thenReturn(Future.successful(Seq(
          User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, teamsAndRoles = None),
        )))

      when(userManagementConnector.getAllTeams())
        .thenReturn(Future.successful(Seq(
          Team(members = Seq.empty, teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        )))

      when(userManagementConnector.getMembersForTeam("team1"))
        .thenReturn(Future.successful(Some(Seq(
          TeamMember(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, role = "user")
        ))))

      when(usersRepository.findAllUsernames())
        .thenReturn(Future.successful(Seq("joe.bloggs", "jane.doe")))

      when(teamsRepository.findAllTeamNames())
        .thenReturn(Future.successful(Seq("team1", "team2")))

      service.updateUsersAndTeams().futureValue

      verify(usersRepository).replaceOrInsertMany(Seq(
        User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, teamsAndRoles = Some(Seq(TeamAndRole(teamName = "team1", role = "user")))),
      ))

      verify(teamsRepository).replaceOrInsertMany(Seq(
        Team(members = Seq(Member(username = "joe.bloggs", role = "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
      ))

      verify(usersRepository).deleteMany(Seq("jane.doe"))
      verify(teamsRepository).deleteMany(Seq("team2"))
    }
  }
}

trait Setup {
  val userManagementConnector = mock[UserManagementConnector]
  val usersRepository         = mock[UsersRepository]
  val teamsRepository         = mock[TeamsRepository]

  val service = new DataRefreshService(userManagementConnector, usersRepository, teamsRepository)

  when(teamsRepository.replaceOrInsertMany(any[Seq[Team]]))
    .thenReturn(Future.successful( () ))

  when(usersRepository.replaceOrInsertMany(any[Seq[User]]))
    .thenReturn(Future.successful( () ))

  when(teamsRepository.deleteMany(any[Seq[String]]))
    .thenReturn(Future.successful( () ))

  when(usersRepository.deleteMany(any[Seq[String]]))
    .thenReturn(Future.successful( () ))

}

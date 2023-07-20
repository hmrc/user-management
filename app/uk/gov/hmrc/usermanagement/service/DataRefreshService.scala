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

import cats.implicits.toFoldableOps
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.usermanagement.connectors.UserManagementConnector
import uk.gov.hmrc.usermanagement.model.{Identity, Member, Team, TeamAndRole, TeamMember, User}
import uk.gov.hmrc.usermanagement.persistence.{TeamsRepository, UsersRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DataRefreshService @Inject()(
  userManagementConnector: UserManagementConnector,
  usersRepository: UsersRepository,
  teamsRepository: TeamsRepository
) extends Logging {

  def updateUsersAndTeams()(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {
    for {
      //Get latestData from UMP
      latestUmpUsers          <- userManagementConnector.getAllUsers()
      latestUmpUsernames      =  latestUmpUsers.map(_.username)
      latestUmpTeams          <- userManagementConnector.getAllTeams()
      latestUmpTeamNames      =  latestUmpTeams.map(_.teamName)
      _                       =  logger.info("Successfully retrieved the latest users and teams data from UMP")
      //Get current data from mongo collections.
      currentUsernames        <- usersRepository.findAllUsernames()
      currentTeamNames        <- teamsRepository.findAllTeamNames()
      //Calculate users and teams to be deleted (as they weren't in latest UMP data).
      usersToDelete           =  currentUsernames.filterNot(latestUmpUsernames.contains(_))
      teamsToDelete           =  currentTeamNames.filterNot(latestUmpTeamNames.contains(_))
      //Shape data to desired format
      humanUsers              =  removeNonHumanIdentities(latestUmpUsers)
      _                       =  logger.info(s"Removed ${latestUmpUsers.length - humanUsers.length} non-human users from the UMP data.")
      finalTeams              <- getFinalTeams(latestUmpTeams)
      finalUsers              =  addTeamAndRolesToUsers(humanUsers, finalTeams)
      //Update mongo collections
      _                       <- usersRepository.replaceOrInsertMany(finalUsers)
      _                       <- teamsRepository.replaceOrInsertMany(finalTeams)
      _                       =  logger.info(s"The following users are no longer in UMP, and will be deleted from the collection: ${usersToDelete.mkString}")
      _                       <- usersRepository.deleteMany(usersToDelete)
      _                       =  logger.info(s"The following teams are no longer in UMP, and will be deleted from the collection: ${teamsToDelete.mkString}")
      _                       <- teamsRepository.deleteMany(teamsToDelete)
      _                       =  logger.info("Successfully refreshed teams and users data from UMP.")
    } yield ()
  }


  //Note this step is required, in order to get the roles for each user. This data is not available from the getAllTeams/GetAllUsers calls.
  //GetAllTeams also has a bug, in which the `members` field always returns an empty array.
  private def getFinalTeams(teams: Seq[Team])(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[Team]] =
    teams.foldLeftM[Future,  Seq[Team]](Seq.empty[Team]) {
      case (updatedTeams, team) =>
        for {
          teamMembers      <- userManagementConnector.getMembersForTeam(team.teamName).map {
                                case Some(tm) => tm
                                case None     => Seq.empty
                              }
          humanTeamMembers = removeNonHumanIdentities(teamMembers)
          asMembers        = humanTeamMembers.map(tm => Member(tm.username, tm.role)).sortBy(_.username)
          newTeam          = team.copy(members = asMembers)
        } yield updatedTeams :+ newTeam
    }

  private def addTeamAndRolesToUsers(users: Seq[User], teams: Seq[Team]): Seq[User] = {
    val teamAndMembers: Seq[(String, Member)] = teams.flatMap(team => team.members.map(member => team.teamName -> member))
    users.map{
      user =>
        val membershipsForUser    = teamAndMembers.filter(_._2.username == user.username)
        val teamsAndRolesForUser  = membershipsForUser.map{ case (team, membership) => TeamAndRole(team, membership.role)}.sortBy(_.teamName)
        user.copy(teamsAndRoles   = Some(teamsAndRolesForUser))
    }
  }

  private val nonHumanIdentifiers: Seq[String] = Seq("service", "platops", "build", "deploy", "deskpro", "ddcops", "platsec")

  private def removeNonHumanIdentities[A <: Identity](identities: Seq[A]): Seq[A] =
    identities.filterNot(user => nonHumanIdentifiers.exists(user.username.toLowerCase.contains(_)))

}

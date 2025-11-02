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

package uk.gov.hmrc.usermanagement.controllers

import cats.implicits.*
import org.apache.pekko.stream.Materializer
import play.api.Logging
import play.api.libs.json.*
import play.api.libs.json.Json.toJson
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.usermanagement.connectors.{SlackConnector, UmpConnector}
import uk.gov.hmrc.usermanagement.model.*
import uk.gov.hmrc.usermanagement.persistence.{SlackChannelCacheRepository, TeamsRepository, UsersRepository}
import uk.gov.hmrc.usermanagement.service.UserAccessService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserManagementController @Inject()(
    cc                    : ControllerComponents,
    umpConnector          : UmpConnector,
    userAccessService     : UserAccessService,
    usersRepository       : UsersRepository,
    teamsRepository       : TeamsRepository,
    slackConnector        : SlackConnector,
    slackChannelCacheRepository: SlackChannelCacheRepository
  )(using
    ExecutionContext, Materializer
) extends BackendController(cc) with Logging:

  private given Writes[User] = User.format
  private given Writes[Team] = Team.format
  def getUsers(team: Option[String], github: Option[String]): Action[AnyContent] = Action.async:
    usersRepository.find(team, github)
      .map: res =>
        Ok(Json.toJson(res.sortBy(_.username)))

  def getAllTeams(includeNonHuman: Boolean): Action[AnyContent] = Action.async { implicit request =>
    for {
      teams <- teamsRepository.findAll()
      teamsWithSlack <- Future.sequence(teams.map { team =>
        for {
          slackChannel <- getOrFetchChannelPrivacy(team.slack)
          slackNotificationChannel <- getOrFetchChannelPrivacy(team.slackNotification)
        } yield TeamSlackChannelResponse(
          members = team.members,
          teamName = team.teamName,
          description = team.description,
          documentation = team.documentation,
          slack = slackChannel,
          slackNotification = slackNotificationChannel
        )
      })
    } yield {
      val result = if includeNonHuman then teamsWithSlack else teamsWithSlack.map(t => t.copy(members = t.members.filterNot(_.isNonHuman)))
      val res = Json.toJson(result.sortBy(_.teamName))
      Ok(res)
    }
  }

  def getAllTeamsOld(includeNonHuman: Boolean): Action[AnyContent] = Action.async:
    teamsRepository.findAll()
      .map: res =>
        if includeNonHuman then
          Ok(Json.toJson(res.sortBy(_.teamName)))
        else
          val filtered = res.map(team => team.copy(members = team.members.filterNot(_.isNonHuman)))
          Ok(Json.toJson(filtered))

  def getUserByUsername(username: String): Action[AnyContent] = Action.async:
    usersRepository.findByUsername(username)
      .map:
        _.fold(NotFound: Result)(res => Ok(Json.toJson(res)))

  def getUsersByQuery(
    query          : Seq[String],
    includeDeleted : Boolean,
    includeNonHuman: Boolean
  ): Action[AnyContent] = Action.async:
    if   query.length > 3 // cap number of searchable terms at 3
    then Future.successful(BadRequest(toJson(ErrorResponse(BAD_REQUEST, "Too many search terms - maximum of 3"))))
    else usersRepository.search(query, includeDeleted, includeNonHuman)
           .map: res =>
             Ok(Json.toJson(res))

  def createUser: Action[CreateUserRequest] =
    Action.async(parse.json[CreateUserRequest](CreateUserRequest.reads)):
      implicit request =>
        umpConnector.createUser(request.body).map(_ => Created)

  def editUserDetails: Action[EditUserDetailsRequest] =
    Action.async(parse.json[EditUserDetailsRequest](EditUserDetailsRequest.reads)):
      implicit request =>
        usersRepository.findByUsername(request.body.username).flatMap:
          case Some(existingUser) =>
            umpConnector.editUserDetails(request.body).flatMap: _ =>
              val updatedUser = request.body.attribute match
                case UserAttribute.DisplayName  => existingUser.copy(displayName    = Some(request.body.value))
                case UserAttribute.Github       => existingUser.copy(githubUsername = Some(request.body.value))
                case UserAttribute.Organisation => existingUser.copy(organisation   = Some(request.body.value))
                case UserAttribute.PhoneNumber  => existingUser.copy(phoneNumber    = Some(request.body.value))
              usersRepository.updateOne(updatedUser).map(_ => Accepted)
          case None =>
            umpConnector.editUserDetails(request.body).map:_ =>
              logger.info(s"Updated successfully on UMP but username '${request.body.username}' not found in mongo. Awaiting scheduler for mongo update.")
              Accepted

  def createTeam: Action[CreateTeamRequest] =
    Action.async(parse.json[CreateTeamRequest](CreateTeamRequest.formats)):
      implicit request =>
        umpConnector.createTeam(request.body).flatMap: _ =>
          teamsRepository.findByTeamName(request.body.team).flatMap:
            case None =>
              teamsRepository.updateOne(Team(Seq.empty[Member], request.body.team, None, None, None, None))
                .map(_ => Created)
            case _ =>
              logger.info(s"Team created successfully on UMP but already exists in mongo cache. Awaiting scheduler for mongo update.")
              Future.successful(Created)

  def deleteTeam(teamName: String): Action[AnyContent] = Action.async:
    implicit request =>
      umpConnector.deleteTeam(teamName).flatMap: _ =>
        teamsRepository.deleteOne(teamName).map(_ => Ok)

  def editTeamDetails: Action[EditTeamDetails] =
    Action.async(parse.json[EditTeamDetails](EditTeamDetails.reads)):
      implicit request =>
        umpConnector.editTeamDetails(request.body).flatMap: _ =>
          teamsRepository.updateTeamDetails(
            teamName          = request.body.team,
            members           = None,
            description       = request.body.description,
            documentation     = request.body.documentation,
            slack             = request.body.slack,
            slackNotification = request.body.slackNotification
          ).map(_ => Accepted)

  def editUserAccess: Action[EditUserAccessRequest] =
    Action.async(parse.json[EditUserAccessRequest](EditUserAccessRequest.reads)):
      implicit request =>
        umpConnector.editUserAccess(request.body).map(_ => Accepted)

  def getUserAccess(username: String): Action[AnyContent] = Action.async:
    implicit request =>
      userAccessService.getUserAccess(username)
        .map:
          _.fold(NotFound: Result)(res => Ok(Json.toJson(res)(UserAccess.writes)))

  def getUserRoles(username: String): Action[AnyContent] = Action.async:
    implicit request =>
      given Writes[UserRoles] = UserRoles.writes
      umpConnector.getUserRoles(username)
        .map(res => Ok(Json.toJson(res)))

  def editUserRoles(username: String): Action[UserRoles] =
    Action.async(parse.json[UserRoles](UserRoles.reads)):
      implicit request =>
        umpConnector.editUserRoles(username, request.body).map(_ => Accepted)

  def resetUserLdapPassword: Action[ResetLdapPassword] =
    Action.async(parse.json[ResetLdapPassword](ResetLdapPassword.reads)):
      implicit request =>
        umpConnector.resetUserLdapPassword(request.body).map(json => Ok(json))

  def resetUserGooglePassword: Action[ResetGooglePassword] =
    Action.async(parse.json[ResetGooglePassword](ResetGooglePassword.reads)):
      implicit request =>
        umpConnector.resetUserGooglePassword(request.body).map(_ => Accepted)

  private def getOrFetchChannelPrivacy(
                                        channelUrl: Option[String]
                                      )(using HeaderCarrier): Future[Option[TeamSlackChannel]] = {

    channelUrl match {
      case None => Future.successful(None)

      case Some(url) =>

        slackChannelCacheRepository.findByChannelName(url).flatMap {
          case Some(slackChannelCache) =>
            Future.successful(Some(TeamSlackChannel(url, slackChannelCache.isPrivate)))

          case None =>
            slackConnector.listAllChannels().flatMap { allChannels =>
              val isPrivate = allChannels.exists(c => c.name == url && c.isPrivate)

              slackChannelCacheRepository.upsert(url, isPrivate).failed.foreach { e =>
                logger.warn(s"Failed to update cache for channel $url", e)
              }

              Future.successful(Some(TeamSlackChannel(url, isPrivate)))
            }
        }
    }
  }

  def getTeamByTeamName(teamName: String, includeNonHuman: Boolean): Action[AnyContent] = Action.async { implicit request =>
    for {
      maybeTeam  <- teamsRepository.findByTeamName(teamName)
      slackChannel <- getOrFetchChannelPrivacy(maybeTeam.flatMap(_.slack))
      slackNotificationChannel <- getOrFetchChannelPrivacy(maybeTeam.flatMap(_.slackNotification))
    } yield {
      maybeTeam.fold(NotFound: Result) { team =>
        val teamSlackChannelResponse = TeamSlackChannelResponse(
          members = team.members,
          teamName = team.teamName,
          description = team.description,
          documentation = team.documentation,
          slack = slackChannel,
          slackNotification = slackNotificationChannel
        )

        if (includeNonHuman) {
          Ok(Json.toJson(teamSlackChannelResponse))
        } else {
          val filtered = teamSlackChannelResponse.copy(
            members = teamSlackChannelResponse.members.filterNot(_.isNonHuman)
          )
          Ok(Json.toJson(filtered))
        }
      }
    }
  }

  def manageVpnAccess(username: String, enableVpn: Boolean): Action[AnyContent] = Action.async:
    implicit request =>
      userAccessService.manageVpnAccess(username, enableVpn)
        .map(_ => Accepted)

  def manageDevToolsAccess(username: String, enableDevTools: Boolean): Action[AnyContent] = Action.async:
    implicit request =>
      userAccessService.manageDevToolsAccess(username, enableDevTools)
        .map(_ => Accepted)

  def requestNewVpnCert(username: String): Action[AnyContent] = Action.async:
    implicit request =>
      umpConnector.requestNewVpnCert(username).map(json => Created(json))

  def addUserToGithubTeam: Action[AddUserToGithubTeamRequest] =
    Action.async(parse.json[AddUserToGithubTeamRequest](AddUserToGithubTeamRequest.reads)):
      implicit request =>
        umpConnector.addUserToGithubTeam(request.body.username, request.body.team).map(_ => Ok)

  def removeUserFromTeam: Action[ManageTeamMembersRequest] =
    Action.async(parse.json[ManageTeamMembersRequest](ManageTeamMembersRequest.reads)):
      implicit request =>
        ( teamsRepository.findByTeamName(request.body.team)
        , usersRepository.findByUsername(request.body.username)
        ).mapN {
          ( optTeam
          , optUser
          ) =>
            (optTeam, optUser) match
              case (Some(team), Some(user)) =>
                for
                  _           <- umpConnector.removeUserFromTeam(request.body.team, request.body.username)
                  updatedTeam =  team.copy(members = team.members.filterNot(_.username == user.username))
                  updatedUser =  user.copy(teamNames = user.teamNames.filterNot(_ == team.teamName))
                  _           <- teamsRepository.updateOne(updatedTeam)
                  _           <- usersRepository.updateOne(updatedUser)
                yield Ok
              case _ =>
                umpConnector.removeUserFromTeam(request.body.team, request.body.username).map:_ =>
                  logger.info(s"Updated successfully on UMP but unable to update mongo cache. Awaiting scheduler for mongo update.")
                  Ok
        }.flatten

  def addUserToTeam: Action[ManageTeamMembersRequest] =
    Action.async(parse.json[ManageTeamMembersRequest](ManageTeamMembersRequest.reads)):
      implicit request =>
        ( teamsRepository.findByTeamName(request.body.team)
        , usersRepository.findByUsername(request.body.username)
        ).mapN {
          ( optTeam
          , optUser
          ) =>
            (optTeam, optUser) match
              case (Some(team), Some(user)) =>
                if (!team.members.exists(_.username == user.username))
                  for
                    _           <- umpConnector.addUserToTeam(request.body.team, request.body.username)
                    updatedTeam =  team.copy(members = team.members :+ Member(user.username, user.displayName, user.primaryEmail, user.role, user.isNonHuman))
                    updatedUser =  user.copy(teamNames = user.teamNames :+ team.teamName)
                    _           <- teamsRepository.updateOne(updatedTeam)
                    _           <- usersRepository.updateOne(updatedUser)
                  yield Ok
                else
                  // User is already in team, just call UMP anyway for completeness
                  umpConnector.addUserToTeam(request.body.team, request.body.username).map(_ => Ok)
              case _ =>
                umpConnector.addUserToTeam(request.body.team, request.body.username).map: _ =>
                  logger.info(s"Updated successfully on UMP but unable to update mongo cache. Awaiting scheduler for mongo update.")
                  Ok

        }.flatten

  def offBoardUsers: Action[OffBoardUsersRequest] =
    Action.async(parse.json[OffBoardUsersRequest](OffBoardUsersRequest.reads)):
      implicit request =>
        for
          users <- request.body.usernames.toSeq.foldLeftM(Set.empty[User]): (acc, username) =>
                     usersRepository.findByUsername(username).map:
                        case Some(user) => acc + user
                        case _          => logger.warn(s"Offboarding users request - not found user info for: $username")
                                           acc
          _     <- umpConnector.offboardUsers(OffBoardUsersRequest(users.map(_.username)))
        yield Ok

end UserManagementController

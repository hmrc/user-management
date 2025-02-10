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
import play.api.Logging
import play.api.libs.json.*
import play.api.libs.json.Json.toJson
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse
import uk.gov.hmrc.usermanagement.connectors.UmpConnector
import uk.gov.hmrc.usermanagement.model.*
import uk.gov.hmrc.usermanagement.persistence.{TeamsRepository, UsersRepository}
import uk.gov.hmrc.usermanagement.service.UserAccessService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserManagementController @Inject()(
  cc               : ControllerComponents,
  umpConnector     : UmpConnector,
  userAccessService: UserAccessService,
  usersRepository  : UsersRepository,
  teamsRepository  : TeamsRepository
)(using
  ExecutionContext
) extends BackendController(cc) with Logging:

  private given Writes[User] = User.format
  private given Writes[Team] = Team.format

  def getUsers(team: Option[String], github: Option[String]): Action[AnyContent] = Action.async:
    usersRepository.find(team, github)
      .map: res =>
        Ok(Json.toJson(res.sortBy(_.username)))

  def getAllTeams(includeNonHuman: Boolean): Action[AnyContent] = Action.async:
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

  def resetUserLdapPassword: Action[ResetLdapPassword] =
    Action.async(parse.json[ResetLdapPassword](ResetLdapPassword.reads)):
      implicit request =>
        umpConnector.resetUserLdapPassword(request.body).map(json => Ok(json))

  def resetUserGooglePassword: Action[ResetGooglePassword] =
    Action.async(parse.json[ResetGooglePassword](ResetGooglePassword.reads)):
      implicit request =>
        umpConnector.resetUserGooglePassword(request.body).map(_ => Accepted)

  def getTeamByTeamName(teamName: String, includeNonHuman: Boolean): Action[AnyContent] = Action.async:
    teamsRepository.findByTeamName(teamName)
      .map:
        _.fold(NotFound: Result): res =>
          if includeNonHuman then
            Ok(Json.toJson(res))
          else
            val filtered = res.copy(members = res.members.filterNot(_.isNonHuman))
            Ok(Json.toJson(filtered))

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
                for
                  _           <- umpConnector.addUserToTeam(request.body.team, request.body.username)
                  updatedTeam =  team.copy(members = team.members :+ Member(user.username, user.displayName, user.role, user.isNonHuman))
                  updatedUser =  user.copy(teamNames = user.teamNames :+ team.teamName)
                  _           <- teamsRepository.updateOne(updatedTeam)
                  _           <- usersRepository.updateOne(updatedUser)
                yield Ok
              case _ =>
                umpConnector.addUserToTeam(request.body.team, request.body.username).map: _ =>
                  logger.info(s"Updated successfully on UMP but unable to update mongo cache. Awaiting scheduler for mongo update.")
                  Ok

        }.flatten
end UserManagementController

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

  def createUser: Action[JsValue] = Action.async(parse.json):
    implicit request =>
      request.body.validate[CreateUserRequest](CreateUserRequest.reads) match
        case JsSuccess(value, path) =>
          umpConnector.createUser(value).map(_ => Created)
        case JsError(errors) =>
          Future.successful(BadRequest(s"Invalid JSON, unable to process due to errors: $errors"))

  def editUserDetails: Action[JsValue] = Action.async(parse.json):
    implicit request =>
      request.body.validate[EditUserDetailsRequest](EditUserDetailsRequest.reads) match
        case JsSuccess(editRequest, _) =>
          usersRepository.findByUsername(editRequest.username).flatMap:
            case Some(existingUser) =>
              umpConnector.editUserDetails(editRequest).flatMap: _ =>
                val updatedUser = editRequest.attribute match
                  case UserAttribute.DisplayName  => existingUser.copy(displayName    = Some(editRequest.value))
                  case UserAttribute.Github       => existingUser.copy(githubUsername = Some(editRequest.value))
                  case UserAttribute.Organisation => existingUser.copy(organisation   = Some(editRequest.value))
                  case UserAttribute.PhoneNumber  => existingUser.copy(phoneNumber    = Some(editRequest.value))
                usersRepository.updateOne(updatedUser).map(_ => Accepted)
            case None =>
              umpConnector.editUserDetails(editRequest).map:_ =>
                logger.info(s"Updated successfully on UMP but username '${editRequest.username}' not found in mongo. Awaiting scheduler for mongo update.")
                Accepted
        case JsError(errors) =>
          Future.successful(BadRequest(s"Invalid JSON, unable to process due to errors: $errors"))

  def editUserAccess: Action[JsValue] = Action.async(parse.json):
    implicit request =>
      request.body.validate[EditUserAccessRequest](EditUserAccessRequest.reads) match
        case JsSuccess(value, path) =>
          umpConnector.editUserAccess(value).map(_ => Accepted)
        case JsError(errors) =>
          Future.successful(BadRequest(s"Invalid JSON, unable to process due to errors: $errors"))
          
  def getUserAccess(username: String): Action[AnyContent] = Action.async:
    implicit request =>
      userAccessService.getUserAccess(username)
        .map:
          _.fold(NotFound: Result)(res => Ok(Json.toJson(res)(UserAccess.writes)))

  def resetUserLdapPassword: Action[JsValue] = Action.async(parse.json):
    implicit request =>
      request.body.validate[ResetLdapPassword](ResetLdapPassword.reads) match
        case JsSuccess(value, path) =>
          umpConnector.resetUserLdapPassword(value).map(json => Ok(json))
        case JsError(errors) =>
          Future.successful(BadRequest(s"Invalid JSON, unable to process due to errors: $errors"))

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

  def addUserToGithubTeam: Action[JsValue] = Action.async(parse.json):
    implicit request =>
      request.body.validate[AddUserToGithubTeamRequest](AddUserToGithubTeamRequest.reads) match
        case JsSuccess(req, path) =>
          umpConnector.addUserToGithubTeam(req.username, req.team).map(_ => Ok)
        case JsError(errors) =>
          Future.successful(BadRequest(s"Invalid JSON, unable to process due to errors: $errors"))

end UserManagementController

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
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import play.api.libs.json.Json.toJson
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse
import uk.gov.hmrc.usermanagement.connectors.UmpConnector
import uk.gov.hmrc.usermanagement.model.{CreateUserRequest, EditUserAccessRequest, Team, User, UserAccess}
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

  val getAllTeams: Action[AnyContent] = Action.async:
    teamsRepository.findAll()
      .map: res =>
        Ok(Json.toJson(res.sortBy(_.teamName)))

  def getUserByUsername(username: String): Action[AnyContent] = Action.async:
    usersRepository.findByUsername(username)
      .map:
        _.fold(NotFound: Result)(res => Ok(Json.toJson(res)))

  def getUsersByQuery(query: Seq[String]): Action[AnyContent] = Action.async:
    if   query.length > 3 // cap number of searchable terms at 3
    then Future.successful(BadRequest(toJson(ErrorResponse(BAD_REQUEST, "Too many search terms - maximum of 3"))))
    else usersRepository.search(query)
           .map: res =>
             Ok(Json.toJson(res))

  def createUser: Action[JsValue] = Action.async(parse.json):
    implicit request =>
      request.body.validate[CreateUserRequest](CreateUserRequest.reads) match
        case JsSuccess(value, path) =>
          umpConnector.createUser(value).map(_ => Created)
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

  def getTeamByTeamName(teamName: String): Action[AnyContent] = Action.async:
    teamsRepository.findByTeamName(teamName)
      .map:
        _.fold(NotFound: Result)(res => Ok(Json.toJson(res)))

  def requestNewVpnCert(username: String): Action[AnyContent] = Action.async:
    implicit request =>
      umpConnector.requestNewVpnCert(username).map(json => Created(json))
end UserManagementController

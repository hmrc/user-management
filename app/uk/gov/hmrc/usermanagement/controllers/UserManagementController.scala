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
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.usermanagement.connectors.UmpConnector
import uk.gov.hmrc.usermanagement.model.{CreateUserRequest, Team, User}
import uk.gov.hmrc.usermanagement.persistence.{TeamsRepository, UsersRepository}
import uk.gov.hmrc.http.HttpErrorFunctions.is2xx

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserManagementController @Inject()(
  cc             : ControllerComponents,
  umpConnector   : UmpConnector,
  usersRepository: UsersRepository,
  teamsRepository: TeamsRepository
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

  def createUser: Action[JsValue] = Action.async(parse.json):
    implicit request =>
      request.body.validate[CreateUserRequest](CreateUserRequest.reads) match
        case JsSuccess(value, path) =>
          umpConnector.createUser(value).map: res =>
            res.status match
              case status if is2xx(status) => Created
              case status                  => Status(status)(res.body)
        case JsError(errors) =>
          Future.successful(BadRequest(s"Invalid JSON, unable to process due to errors: $errors"))

  def getTeamByTeamName(teamName: String): Action[AnyContent] = Action.async:
    teamsRepository.findByTeamName(teamName)
      .map:
        _.fold(NotFound: Result)(res => Ok(Json.toJson(res)))
end UserManagementController

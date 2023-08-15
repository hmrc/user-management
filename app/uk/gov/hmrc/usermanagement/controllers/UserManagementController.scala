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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.usermanagement.model.{Team, User}
import uk.gov.hmrc.usermanagement.persistence.{TeamsRepository, UsersRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class UserManagementController @Inject()(
  cc                        : ControllerComponents,
  usersRepository           : UsersRepository,
  teamsRepository           : TeamsRepository
)(implicit ec: ExecutionContext
) extends BackendController(cc) with Logging
{
  def getUsers(team: Option[String], github: Option[String]): Action[AnyContent] = Action.async {
      implicit val uf = User.format
      usersRepository.find(team, github).map(res => Ok(Json.toJson(res.sortBy(_.username))))
  }

  val getAllTeams: Action[AnyContent] = Action.async {
      implicit val uf = Team.format
      teamsRepository.findAll().map(res => Ok(Json.toJson(res.sortBy(_.teamName))))
  }

  def getUserByUsername(username: String): Action[AnyContent] = Action.async {
      implicit val uf = User.format
      usersRepository.findByUsername(username)
        .map(_.fold(NotFound: Result)(res => Ok(Json.toJson(res))))
  }

  def getTeamByTeamName(teamName: String): Action[AnyContent] = Action.async {
      implicit val uf = Team.format
      teamsRepository.findByTeamName(teamName)
        .map(_.fold(NotFound: Result)(res => Ok(Json.toJson(res))))
  }
}

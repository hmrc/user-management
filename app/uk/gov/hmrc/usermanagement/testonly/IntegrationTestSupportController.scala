/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.usermanagement.testonly

import play.api.libs.json.{JsError, OFormat, Reads}
import play.api.mvc.{Action, AnyContent, BodyParser, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.usermanagement.model.Team
import uk.gov.hmrc.usermanagement.persistence.TeamsRepository
import org.mongodb.scala.bson.Document
import org.mongodb.scala.ObservableFuture

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class IntegrationTestSupportController @Inject()(
  teamsRepository: TeamsRepository,
  cc             : ControllerComponents
)(using
  ExecutionContext
) extends BackendController(cc):

  private def validateJson[A: Reads]: BodyParser[A] =
    parse.json.validate(
      _.validate[A].asEither.left
      .map: e =>
        BadRequest(JsError.toJson(e))
    )

  private given OFormat[Team] = Team.format

  def addTeams(): Action[Seq[Team]] = Action.async(validateJson[Seq[Team]]): request =>
    teamsRepository.putAll(request.body)
      .map:_ =>
        Ok("Ok")

  def deleteTeams(): Action[AnyContent] = Action.async:
    teamsRepository.collection.deleteMany(Document())
      .toFuture().map: _ =>
        Ok("Ok")
end IntegrationTestSupportController

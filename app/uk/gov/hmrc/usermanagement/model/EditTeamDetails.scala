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

package uk.gov.hmrc.usermanagement.model

import play.api.libs.functional.syntax.*
import play.api.libs.json.*

case class EditTeamDetails(
  team             : String,
  description      : Option[String],
  documentation    : Option[String],
  slack            : Option[String],
  slackNotification: Option[String]
)

object EditTeamDetails:
  val reads: Reads[EditTeamDetails] =
    ( (__ \ "team"             ).read[String]
    ~ (__ \ "description"      ).readNullable[String]
    ~ (__ \ "documentation"    ).readNullable[String]
    ~ (__ \ "slack"            ).readNullable[String]
    ~ (__ \ "slackNotification").readNullable[String]
    )(apply).filter(JsonValidationError("At least one field must be provided")): request =>
      Seq(request.description, request.documentation, request.slack, request.slackNotification).exists(_.isDefined)

  val writes: Writes[EditTeamDetails] =
    ( (__ \ "description"      ).writeNullable[String]
    ~ (__ \ "documentation"    ).writeNullable[String]
    ~ (__ \ "slack"            ).writeNullable[String]
    ~ (__ \ "slackNotification").writeNullable[String]
    )(request => (request.description, request.documentation, request.slack, request.slackNotification))

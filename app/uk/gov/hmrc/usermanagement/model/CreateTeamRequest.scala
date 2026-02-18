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

package uk.gov.hmrc.usermanagement.model

import play.api.libs.functional.syntax.*
import play.api.libs.json.*

case class CreateTeamRequest(
  platform  :String,
  team      :String
)

object CreateTeamRequest:
  val reads: Reads[CreateTeamRequest] =
    ( (__ \ "platform").read[String]
    ~ (__ \ "team"    ).read[String]
    )(CreateTeamRequest.apply)

  val writes: Writes[CreateTeamRequest] = Writes { req =>
    Json.obj(
      "tags" -> Json.arr(
        Json.obj(
          "tag"    -> "Platform",
          "values" -> Json.arr(req.platform)
        )
      ),
      "team" -> req.team
    )
  }

  val formats: Format[CreateTeamRequest] = Format(reads, writes)

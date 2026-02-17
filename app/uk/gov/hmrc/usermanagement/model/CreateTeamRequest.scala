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
  val formats: Format[CreateTeamRequest] =
    ( (__ \ "platform").format[String]
    ~ (__ \ "team"    ).format[String]
    )(apply, c => Tuple.fromProductTyped(c))

case class CreateTeamUMPRequest(
  platform  :Seq[String],
  team      :String
)
object CreateTeamUMPRequest:
  val formats: Format[CreateTeamUMPRequest] =
    ( (__ \ "platform").format[Seq[String]] 
    ~ (__ \ "team"    ).format[String]
    )(apply, c => Tuple.fromProductTyped(c))  

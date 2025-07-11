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

final case class SlackUser(
  email    : Option[String],
  id       : String,
  name     : String,
  isBot    : Boolean,
  isDeleted: Boolean
)

object SlackUser:
  val apiReads: Reads[SlackUser] =
    ( (__ \ "profile" \ "email").readNullable[String]
    ~ (__ \ "id"               ).read[String]
    ~ (__ \ "name"             ).read[String]
    ~ (__ \ "is_bot"           ).read[Boolean]
    ~ (__ \ "deleted"          ).read[Boolean]
    )(SlackUser.apply)

  val mongoFormat: Format[SlackUser] =
    ( (__ \ "email"    ).formatNullable[String]
    ~ (__ \ "id"       ).format[String]
    ~ (__ \ "name"     ).format[String]
    ~ (__ \ "isBot"    ).format[Boolean]
    ~ (__ \ "isDeleted").format[Boolean]
    )(SlackUser.apply, su => Tuple.fromProductTyped(su))

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

case class Team(
 members          : Seq[Member],
 teamName         : String,
 description      : Option[String],
 documentation    : Option[String],
 slack            : Option[String],
 slackNotification: Option[String]
)

object Team:
  val format: OFormat[Team] =
    given Format[Member] = Member.format
    ( (__ \ "members"          ).format[Seq[Member]]
    ~ (__ \ "teamName"         ).format[String]
    ~ (__ \ "description"      ).formatNullable[String]
    ~ (__ \ "documentation"    ).formatNullable[String]
    ~ (__ \ "slack"            ).formatNullable[String]
    ~ (__ \ "slackNotification").formatNullable[String]
    )(Team.apply, pt => Tuple.fromProductTyped(pt))
  
case class Member(
  username   : String,
  displayName: Option[String],
  role       : String,
  isNonHuman : Boolean
)

object Member:
  val format: OFormat[Member] =
    ( (__ \ "username"   ).format[String]
    ~ (__ \ "displayName").formatNullable[String]
    ~ (__ \ "role"       ).format[String]
    ~ (__ \ "isNonHuman" ).formatWithDefault[Boolean](false)
    )(Member.apply, pt => Tuple.fromProductTyped(pt))

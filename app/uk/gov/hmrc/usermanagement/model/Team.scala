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

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{OFormat, __}

case class Team(
 members          : Seq[Member],
 teamName         : String,
 description      : Option[String],
 documentation    : Option[String],
 slack            : Option[String],
 slackNotification: Option[String]
)

object Team {

  val format: OFormat[Team] = {
    implicit val tmf = Member.format
    ((__ \ "members"            ).format[Seq[Member]]
      ~ (__ \"teamName"         ).format[String]
      ~ (__ \"description"      ).formatNullable[String]
      ~ (__ \"documentation"    ).formatNullable[String]
      ~ (__ \"slack"            ).formatNullable[String]
      ~ (__ \"slackNotification").formatNullable[String]
      )(Team.apply, unlift(Team.unapply))
  }

}

case class Member(
 username: String,
 role    : String
)

object Member {
  val format: OFormat[Member] = {
    ((__ \ "username").format[String]
      ~ (__ \"role"  ).format[String]
      )(Member.apply, unlift(Member.unapply))
  }
}

case class TeamName(asString: String) extends AnyVal

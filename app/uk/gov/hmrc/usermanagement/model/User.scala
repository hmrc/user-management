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

case class User(
  displayName   : Option[String],
  familyName    : String,
  givenName     : Option[String],
  organisation  : Option[String],
  primaryEmail  : String,
  slackId       : Option[String],
  username      : String,
  githubUsername: Option[String],
  phoneNumber   : Option[String],
  role          : String,
  teamNames     : Seq[String]
)

object User {
  val format: OFormat[User] = {
    ( ( __ \ "displayName"   ).formatNullable[String]
    ~ ( __ \ "familyName"    ).format[String]
    ~ ( __ \ "givenName"     ).formatNullable[String]
    ~ ( __ \ "organisation"  ).formatNullable[String]
    ~ ( __ \ "primaryEmail"  ).format[String]
    ~ ( __ \ "slackId"       ).formatNullable[String]
    ~ ( __ \ "username"      ).format[String]
    ~ ( __ \ "githubUsername").formatNullable[String]
    ~ ( __ \ "phoneNumber"   ).formatNullable[String]
    ~ ( __ \ "role"          ).format[String]
    ~ ( __ \ "teamNames"     ).format[Seq[String]]
    )(User.apply, unlift(User.unapply))
  }
}

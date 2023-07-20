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
import play.api.libs.json.{OFormat, Reads, __}

sealed trait Identity {
  def username: String
}

case class User(
  displayName  : Option[String],
  familyName   : String,
  givenName    : Option[String],
  organisation : Option[String],
  primaryEmail : String,
  username     : String,
  github       : Option[String],
  phoneNumber  : Option[String],
  teamsAndRoles: Option[Seq[TeamAndRole]]
) extends Identity

object User {
  implicit val urf = TeamAndRole.format

  val format: OFormat[User] = {
    (( __ \ "displayName"    ).formatNullable[String]
     ~ ( __ \ "familyName"   ).format[String]
     ~ ( __ \ "givenName"    ).formatNullable[String]
     ~ ( __ \ "organisation" ).formatNullable[String]
     ~ ( __ \ "primaryEmail" ).format[String]
     ~ ( __ \ "username"     ).format[String]
     ~ ( __ \ "github"       ).formatNullable[String]
     ~ ( __ \ "phoneNumber"  ).formatNullable[String]
     ~ ( __ \ "teamsAndRoles").formatNullable[Seq[TeamAndRole]]
    )(User.apply, unlift(User.unapply))
  }
}

case class TeamAndRole(
  teamName: String,
  role    : String
)

object TeamAndRole {
  val format: OFormat[TeamAndRole] = {
    ((__ \ "teamName").format[String]
      ~  (__ \ "role").format[String]
    )(TeamAndRole.apply, unlift(TeamAndRole.unapply))
  }
}

case class TeamMember(
  displayName  : Option[String],
  familyName   : String,
  givenName    : Option[String],
  organisation : Option[String],
  primaryEmail : String,
  username     : String,
  github       : Option[String],
  phoneNumber  : Option[String],
  role         : String
) extends Identity

object TeamMember {

  val reads: Reads[TeamMember] = {
    (( __ \ "displayName"   ).readNullable[String]
      ~ ( __ \ "familyName"  ).read[String]
      ~ ( __ \ "givenName"   ).readNullable[String]
      ~ ( __ \ "organisation").readNullable[String]
      ~ ( __ \ "primaryEmail").read[String]
      ~ ( __ \ "username"    ).read[String]
      ~ ( __ \ "github"      ).readNullable[String]
      ~ ( __ \ "phoneNumber" ).readNullable[String]
      ~ ( __ \ "role"        ).read[String]
      )(TeamMember.apply _)
  }
}


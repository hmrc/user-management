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

import play.api.libs.json.*

case class UserRoles(
  roles: Seq[UserRole]
)

object UserRoles:
  val reads: Reads[UserRoles] =
    given Reads[UserRole] = UserRole.reads
    (__ \ "roles").read[Seq[UserRole]].map(apply)

  val writes: Writes[UserRoles] =
    given Writes[UserRole] = UserRole.writes
    (__ \ "roles").write[Seq[UserRole]].contramap(_.roles)

enum UserRole(val role: String):
  case TeamAdmin            extends UserRole("team_admin"           )
  case TrustedAuthoriser    extends UserRole("trusted_authoriser"   )
  case LocationAuthoriser   extends UserRole("location_authoriser"  )
  case GlobalAuthoriser     extends UserRole("global_authoriser"    )
  case ExperimentalFeatures extends UserRole("experimental_features")

object UserRole:
  private def fromString(value: String): Option[UserRole] =
    UserRole.values.find(_.role == value)

  val reads: Reads[UserRole] =
    __.read[String].flatMap: role =>
      UserRole.fromString(role) match
        case Some(attr) => Reads.pure(attr)
        case None       => Reads(_ => JsError(s"Unknown role: $role"))

  val writes: Writes[UserRole] =
    Writes { attr => JsString(attr.role) }

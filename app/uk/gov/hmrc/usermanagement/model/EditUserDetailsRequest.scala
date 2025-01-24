/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class EditUserDetailsRequest(
  username : String,
  attribute: UserAttribute,
  value    : String
)

object EditUserDetailsRequest:
  val reads: Reads[EditUserDetailsRequest] =
    ( (__ \ "username" ).read[String]
    ~ (__ \ "attribute").read[UserAttribute](UserAttribute.reads)
    ~ (__ \ "value"    ).read[String]
    )(apply)

  val writes: OWrites[EditUserDetailsRequest] =
    OWrites: request =>
      Json.obj(request.attribute.name -> request.value)

enum UserAttribute(val name: String):
  case DisplayName  extends UserAttribute("displayName" )
  case Github       extends UserAttribute("github"      )
  case Organisation extends UserAttribute("organisation")
  case PhoneNumber  extends UserAttribute("phoneNumber" )

object UserAttribute:
  private def fromString(value: String): Option[UserAttribute] =
    UserAttribute.values.find(_.name == value)

  val reads: Reads[UserAttribute] =
    __.read[String].flatMap: name =>
      UserAttribute.fromString(name) match
        case Some(attr) => Reads.pure(attr)
        case None       => Reads(_ => JsError(s"Unknown attribute: $name"))

  val writes: Writes[UserAttribute] =
    Writes { attr => JsString(attr.name) }

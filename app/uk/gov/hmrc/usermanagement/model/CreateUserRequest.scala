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

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class CreateUserRequest(
  contactComments   : String,
  contactEmail      : String,
  familyName        : String,
  givenName         : String,
  organisation      : String,
  team              : String,
  userDisplayName   : String,
  access            : Access,
  isReturningUser   : Boolean,
  isTransitoryUser  : Boolean,
  isExistingLDAPUser: Boolean
)

object CreateUserRequest:
  val reads: Reads[CreateUserRequest] =
    ( (__ \ "contactComments"   ).read[String]
    ~ (__ \ "contactEmail"      ).read[String]
    ~ (__ \ "familyName"        ).read[String]
    ~ (__ \ "givenName"         ).read[String]
    ~ (__ \ "organisation"      ).read[String]
    ~ (__ \ "team"              ).read[String]
    ~ (__ \ "userDisplayName"   ).read[String]
    ~ (__ \ "access"            ).read[Access](Access.reads)
    ~ (__ \ "isReturningUser"   ).read[Boolean]
    ~ (__ \ "isTransitoryUser"  ).read[Boolean]
    ~ (__ \ "isExistingLDAPUser").read[Boolean]
    )(CreateUserRequest.apply _)

  val writes: OWrites[CreateUserRequest] =
    OWrites.transform[CreateUserRequest](
      ( (__ \ "contactComments"   ).write[String]
      ~ (__ \ "contactEmail"      ).write[String]
      ~ (__ \ "familyName"        ).write[String]
      ~ (__ \ "givenName"         ).write[String]
      ~ (__ \ "organisation"      ).write[String]
      ~ (__ \ "team"              ).write[String]
      ~ (__ \ "userDisplayName"   ).write[String]
      ~ (__ \ "access"            ).write[Access](Access.writes)
      ~ (__ \ "isReturningUser"   ).write[Boolean]
      ~ (__ \ "isTransitoryUser"  ).write[Boolean]
      ~ (__ \ "isExistingLDAPUser").write[Boolean]
      )(c => Tuple.fromProductTyped(c))
    ): (req, json) =>
      json ++ Json.obj("username" -> "none")

case class Access(
  ldap: Boolean,
  vpn: Boolean,
  jira: Boolean,
  confluence: Boolean,
  environments: Boolean,
  googleApps: Boolean,
  bitwarden: Boolean
)

object Access:
  val reads: Reads[Access] =
    ( (__ \ "ldap"        ).read[Boolean]
    ~ (__ \ "vpn"         ).read[Boolean]
    ~ (__ \ "jira"        ).read[Boolean]
    ~ (__ \ "confluence"  ).read[Boolean]
    ~ (__ \ "environments").read[Boolean]
    ~ (__ \ "googleApps"  ).read[Boolean]
    ~ (__ \ "bitwarden"   ).read[Boolean]
    )(Access.apply _)

  val writes: OWrites[Access] = (access: Access) =>
    val fields = Seq(
      if access.ldap         then Some("ldap"         -> JsString("todo")) else None,
      if access.vpn          then Some("vpn"          -> JsString("todo")) else None,
      if access.jira         then Some("jira"         -> JsString("todo")) else None,
      if access.confluence   then Some("confluence"   -> JsString("todo")) else None,
      if access.environments then Some("environments" -> JsString("todo")) else None,
      if access.googleApps   then Some("googleApps"   -> JsString("todo")) else None,
      if access.bitwarden    then Some("bitwarden"    -> JsString("todo")) else None
    ).flatten

    JsObject(fields)

case class EditUserAccessRequest(
  username          : String,
  organisation      : String,
  access            : EditAccess,
  isExistingLDAPUser: Boolean
)

object EditUserAccessRequest:
  val reads: Reads[EditUserAccessRequest] =
    ( (__ \ "username"          ).read[String]
    ~ (__ \ "organisation"      ).read[String]
    ~ (__ \ "access"            ).read[EditAccess](EditAccess.reads)
    ~ (__ \ "isExistingLDAPUser").read[Boolean]
    )(EditUserAccessRequest.apply _)

  val writes: OWrites[EditUserAccessRequest] =
    ( (__ \ "username"          ).write[String]
    ~ (__ \ "organisation"      ).write[String]
    ~ (__ \ "access"            ).write[EditAccess](EditAccess.writes)
    ~ (__ \ "isExistingLDAPUser").write[Boolean]
    )(c => Tuple.fromProductTyped(c))

case class EditAccess(
  vpn: Boolean,
  jira: Boolean,
  confluence: Boolean,
  environments: Boolean,
  googleApps: Boolean,
  bitwarden: Boolean
)

object EditAccess:
  val reads: Reads[EditAccess] =
    ( (__ \ "vpn"         ).read[Boolean]
    ~ (__ \ "jira"        ).read[Boolean]
    ~ (__ \ "confluence"  ).read[Boolean]
    ~ (__ \ "environments").read[Boolean]
    ~ (__ \ "googleApps"  ).read[Boolean]
    ~ (__ \ "bitwarden"   ).read[Boolean]
    )(EditAccess.apply _)

  val writes: OWrites[EditAccess] = (access: EditAccess) =>
    val fields = Seq(
      if access.vpn          then Some("vpn"          -> JsString("todo")) else None,
      if access.jira         then Some("jira"         -> JsString("todo")) else None,
      if access.confluence   then Some("confluence"   -> JsString("todo")) else None,
      if access.environments then Some("environments" -> JsString("todo")) else None,
      if access.googleApps   then Some("googleApps"   -> JsString("todo")) else None,
      if access.bitwarden    then Some("bitwarden"    -> JsString("todo")) else None
    ).flatten

    JsObject(fields)

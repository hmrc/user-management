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

import play.api.libs.json.*
import play.api.libs.functional.syntax.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class UserAccess(
  vpn       : Boolean,
  jira      : Boolean,
  confluence: Boolean,
  devTools  : Boolean,
  googleApps: Boolean,
  pagerduty : Boolean
)

object UserAccess:
  val reads: Reads[UserAccess] =
    (__).read[List[String]].map: accessList =>
      val normalizedAccess = accessList.map(_.toLowerCase).toSet
      UserAccess(
        vpn         = normalizedAccess.contains("vpn"),
        jira        = normalizedAccess.contains("jira"),
        confluence  = normalizedAccess.contains("confluence"),
        devTools    = normalizedAccess.contains("dev-tools"),
        googleApps  = normalizedAccess.contains("googleapps"),
        pagerduty   = normalizedAccess.contains("pagerduty")
      )

  val mongoReads: Reads[UserAccess] =
    ( (__ \ "vpn"       ).read[Boolean]
    ~ (__ \ "jira"      ).read[Boolean]
    ~ (__ \ "confluence").read[Boolean]
    ~ (__ \ "devTools"  ).read[Boolean]
    ~ (__ \ "googleApps").read[Boolean]
    ~ (__ \ "pagerduty" ).read[Boolean]
    )(UserAccess.apply _)

  val writes: Writes[UserAccess] =
    (userAccess: UserAccess) => Json.obj(
      "vpn"        -> userAccess.vpn,
      "jira"       -> userAccess.jira,
      "confluence" -> userAccess.confluence,
      "devTools"   -> userAccess.devTools,
      "googleApps" -> userAccess.googleApps,
      "pagerduty"  -> userAccess.pagerduty
    )

  val mongoFormat: Format[UserAccess] =
    Format(mongoReads, writes)

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
import play.api.libs.json.{Reads, __}

import java.time.Instant

case class GitHubTeam(name: TeamName, lastActiveDate: Option[Instant], repos: Seq[String])

object GitHubTeam:
  val reads: Reads[GitHubTeam] =
    given Reads[TeamName] = TeamName.reads
    ( (__ \ "name"          ).read[TeamName]
    ~ (__ \ "lastActiveDate").readNullable[Instant]
    ~ (__ \ "repos"         ).read[Seq[String]]
    )(GitHubTeam.apply _)

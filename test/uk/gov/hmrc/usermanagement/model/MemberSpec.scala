/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{Json, OFormat}

class MemberSpec extends AnyWordSpec with Matchers:

  "Member.format" should:
    "default primaryEmail for backwards-compatible test-only payloads" in:
      val json = Json.obj(
        "username"    -> "test.user",
        "displayName" -> "Test User",
        "role"        -> "user"
      )

      json.as[Member](Member.format).primaryEmail shouldBe "Unknown"

    "preserve an explicitly supplied primaryEmail" in:
      val json = Json.obj(
        "username"     -> "test.user",
        "displayName"  -> "Test User",
        "primaryEmail" -> "test.user@example.com",
        "role"         -> "user"
      )

      json.as[Member](Member.format).primaryEmail shouldBe "test.user@example.com"

    "decode a test-only teams payload when a member omits primaryEmail" in:
      given OFormat[Team] = Team.format
      val json = Json.arr(
        Json.obj(
          "members" -> Json.arr(
            Json.obj(
              "username"    -> "test.user",
              "displayName" -> "Test User",
              "role"        -> "user"
            )
          ),
          "teamName" -> "test-team"
        )
      )

      json.as[Seq[Team]].head.members.head.primaryEmail shouldBe "Unknown"



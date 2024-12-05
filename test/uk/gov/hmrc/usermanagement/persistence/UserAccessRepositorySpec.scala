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

package uk.gov.hmrc.usermanagement.persistence

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.usermanagement.model.{UserAccess, UserWithAccess}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global


class UserAccessRepositorySpec
  extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[UserWithAccess]:

  override val repository: UserAccessRepository = UserAccessRepository(mongoComponent)

  val username = "Joe Bloggs"

  val userWithAccess =
    UserWithAccess(
      username       = username,
      access         = UserAccess(vpn = true, jira = true, confluence = true, devTools = true, googleApps = true),
      createdAt      = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )

  "UserAccessRepository" should:
    "insert the new user's access into the collection and find it when given the username" in:
      repository.put(userWithAccess).futureValue

      val res: Option[UserWithAccess] = repository.findByUsername(username).futureValue
      res shouldBe Some(userWithAccess)

  "UserAccessRepository.findByUsername" should:
    "return None when username is not found" in:
      repository.findByUsername("jane.doe").futureValue shouldBe None
end UserAccessRepositorySpec
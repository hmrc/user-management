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
import uk.gov.hmrc.usermanagement.model.{TeamAndRole, User}

import scala.concurrent.ExecutionContext.Implicits.global


class UserRepositorySpec
  extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[User] {

  override lazy val repository = new UsersRepository(mongoComponent)

  "UsersRepository.insertOrReplaceMany" should {
    "Insert or replace documents" in {
      repository.collection.insertOne(
        User(
          displayName = Some("Joe Bloggs"),
          familyName = "Bloggs",
          givenName = Some("Joe"),
          organisation = Some("MDTP"),
          primaryEmail = "joe.bloggs@gmail.com",
          username = "joe.bloggs",
          github = None,
          phoneNumber = None,
          teamsAndRoles = Some(Seq(TeamAndRole(teamName = "team1", role = "user"))),
      )).toFuture().futureValue

      val latestUsers = Seq(
        User(
          displayName = Some("Joe Bloggs"),
          familyName = "Bloggs",
          givenName = Some("Joe"),
          organisation = Some("MDTP"),
          primaryEmail = "joe.bloggs@gmail.com",
          username = "joe.bloggs",
          github = None,
          phoneNumber = None,
          teamsAndRoles = Some(Seq(TeamAndRole(teamName = "team2", role = "team-admin")))),
        User(
          displayName = Some("Jane Doe"),
          familyName = "Doe",
          givenName = Some("Jane"),
          organisation = Some("MDTP"),
          primaryEmail = "jane.doe@gmail.com",
          username = "jane.doe",
          github = None,
          phoneNumber = None,
          teamsAndRoles = None),
      )

      repository.replaceOrInsertMany(latestUsers).futureValue

      val res = repository.findAll().futureValue
      res.length shouldBe 2

      res should contain theSameElementsAs(latestUsers)
    }

    "Delete many" in {
      repository.collection.insertMany(
        Seq(User(
            displayName = Some("Joe Bloggs"),
            familyName = "Bloggs",
            givenName = Some("Joe"),
            organisation = Some("MDTP"),
            primaryEmail = "joe.bloggs@gmail.com",
            username = "joe.bloggs",
            github = None,
            phoneNumber = None,
            teamsAndRoles = Some(Seq(TeamAndRole(teamName = "team2", role = "team-admin")))),
          User(
            displayName = Some("Jane Doe"),
            familyName = "Doe",
            givenName = Some("Jane"),
            organisation = Some("MDTP"),
            primaryEmail = "jane.doe@gmail.com",
            username = "jane.doe",
            github = None,
            phoneNumber = None,
            teamsAndRoles = None),
        )).toFuture().futureValue

      repository.deleteMany(Seq("joe.bloggs", "jane.doe")).futureValue

      val res = repository.findAll().futureValue

      res.length shouldBe 0
      res shouldBe Seq.empty[User]
    }
  }
}

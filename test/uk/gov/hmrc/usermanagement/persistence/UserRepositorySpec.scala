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
import uk.gov.hmrc.usermanagement.model.{TeamMembership, User}

import scala.concurrent.ExecutionContext.Implicits.global


class UserRepositorySpec
  extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[User] {

  override lazy val repository = new UsersRepository(mongoComponent)

  override protected val checkIndexedQueries: Boolean =
  // we run unindexed queries
    false

  "UsersRepository.putAll" should {
    "delete the existing users, and insert new users into the collection" in {
      repository.collection.insertOne(
        User(
          displayName = Some("Old User"),
          familyName = "Old",
          givenName = Some("User"),
          organisation = Some("MDTP"),
          primaryEmail = "old-user@gmail.com",
          username = "old-user",
          github = None,
          phoneNumber = None,
          teamsAndRoles = Some(Seq(TeamMembership(teamName = "team1", role = "user"))),
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
          teamsAndRoles = Some(Seq(TeamMembership(teamName = "team2", role = "team-admin")))),
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

      repository.putAll(latestUsers).futureValue

      val res = repository.findAll(None).futureValue
      res.length shouldBe 2

      res should contain theSameElementsAs latestUsers
    }

  }

  "UsersRepository.findAll()" should {
    "find all users that are a member of given team name" in {

      val userOne = User(
        displayName   = Some("Joe Bloggs"),
        familyName    = "Bloggs",
        givenName     = Some("Joe"),
        organisation  = Some("MDTP"),
        primaryEmail  = "joe.bloggs@gmail.com",
        username      = "joe.bloggs",
        github        = None,
        phoneNumber   = None,
        teamsAndRoles = Some(Seq(TeamMembership(teamName = "team1", role = "team-admin"), TeamMembership(teamName = "team2", role = "team-admin")))
      )

      val userTwo = User(
        displayName   = Some("John Smith"),
        familyName    = "Smith",
        givenName     = Some("John"),
        organisation  = Some("MDTP"),
        primaryEmail  = "john.smith@gmail.com",
        username      = "john.smith",
        github        = None,
        phoneNumber   = None,
        teamsAndRoles = Some(Seq(TeamMembership(teamName = "team2", role = "team-admin")))
      )

      val userThree   = User(
        displayName   = Some("Jane Doe"),
        familyName    = "Doe",
        givenName     = Some("Jane"),
        organisation  = Some("MDTP"),
        primaryEmail  = "jane.doe@gmail.com",
        username      = "jane.doe",
        github        = None,
        phoneNumber   = None,
        teamsAndRoles = Some(Seq(TeamMembership(teamName = "team1", role = "team-admin")))
      )

      val users = Seq(userOne, userTwo, userThree)

      repository.collection.insertMany(users).toFuture().futureValue

      val res = repository.findAll(Some("team1")).futureValue

      res.length shouldBe 2
      res should contain theSameElementsAs Seq(userOne, userThree)
    }
  }

  "UsersRepository.findByUsername" should {

    val userOne = User(
      displayName   = Some("Joe Bloggs"),
      familyName    = "Bloggs",
      givenName     = Some("Joe"),
      organisation  = Some("MDTP"),
      primaryEmail  = "joe.bloggs@gmail.com",
      username      = "joe.bloggs",
      github        = None,
      phoneNumber   = None,
      teamsAndRoles = Some(Seq(TeamMembership(teamName = "team1", role = "team-admin"), TeamMembership(teamName = "team2", role = "team-admin")))
    )

    val userTwo = User(
      displayName   = Some("John Smith"),
      familyName    = "Smith",
      givenName     = Some("John"),
      organisation  = Some("MDTP"),
      primaryEmail  = "john.smith@gmail.com",
      username      = "john.smith",
      github        = None,
      phoneNumber   = None,
      teamsAndRoles = Some(Seq(TeamMembership(teamName = "team2", role = "team-admin")))
    )

    "return user information for a given username" in {
      repository.collection.insertMany(Seq(userOne, userTwo)).toFuture().futureValue

      repository.findByUsername("joe.bloggs").futureValue shouldBe Some(userOne)
    }

    "return None when username is not found" in {
      repository.collection.insertMany(Seq(userOne, userTwo)).toFuture().futureValue

      repository.findByUsername("jane.doe").futureValue shouldBe None
    }
  }
}

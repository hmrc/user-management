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
import uk.gov.hmrc.usermanagement.model.User
import org.mongodb.scala.ObservableFuture

import scala.concurrent.ExecutionContext.Implicits.global


class UserRepositorySpec
  extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[User]:

  override val repository: UsersRepository =  UsersRepository(mongoComponent)

  override protected val checkIndexedQueries: Boolean =
  // we run unindexed queries
    false

  "UsersRepository.putAll" should:
    "delete the existing users, and insert new users into the collection" in:
      repository.collection.insertOne(
        User(
          displayName    = Some("Old User"),
          familyName     = "Old",
          givenName      = Some("User"),
          organisation   = Some("MDTP"),
          primaryEmail   = "old-user@gmail.com",
          slackId        = None,
          username       = "old-user",
          githubUsername = None,
          phoneNumber    = None,
          role           = "user",
          teamNames      = Seq("team1"),
          isDeleted      = false 
        )
      ).toFuture().futureValue

      val latestUsers = Seq(
        User(
          displayName    = Some("Joe Bloggs"),
          familyName     = "Bloggs",
          givenName      = Some("Joe"),
          organisation   = Some("MDTP"),
          primaryEmail   = "joe.bloggs@gmail.com",
          slackId        = None,
          username       = "joe.bloggs",
          githubUsername = None,
          phoneNumber    = None,
          role           = "user",
          teamNames      = Seq("team2"),
          isDeleted      = false 
        ),
        User(
          displayName    = Some("Jane Doe"),
          familyName     = "Doe",
          givenName      = Some("Jane"),
          organisation   = Some("MDTP"),
          primaryEmail   = "jane.doe@gmail.com",
          slackId        = None,
          username       = "jane.doe",
          githubUsername = None,
          phoneNumber    = None,
          role           = "user",
          teamNames      = Seq("team3"),
          isDeleted      = false 
        )
      )

      repository.putAll(latestUsers).futureValue

      val res = repository.find().futureValue
      res.length shouldBe 2

      res should contain theSameElementsAs latestUsers

  "UsersRepository.find" should:
    "only find users in teams" in:
      val userOne = User(
        displayName    = Some("Joe Bloggs"),
        familyName     = "Bloggs",
        givenName      = Some("Joe"),
        organisation   = Some("MDTP"),
        primaryEmail   = "joe.bloggs@gmail.com",
        slackId        = None,
        username       = "joe.bloggs",
        githubUsername = None,
        phoneNumber    = None,
        role           = "team-admin",
        teamNames      = Seq("team1","team2"),
        isDeleted      = false 
        )
      
      val userTwo = User(
        displayName    = Some("John Smith"),
        familyName     = "Smith",
        givenName      = Some("John"),
        organisation   = Some("MDTP"),
        primaryEmail   = "john.smith@gmail.com",
        slackId        = None,
        username       = "john.smith",
        githubUsername = None,
        phoneNumber    = None,
        role           = "user",
        teamNames      = Seq.empty[String],
        isDeleted      = false 
      )

      val userThree = User(
        displayName    = Some("Jane Doe"),
        familyName     = "Doe",
        givenName      = Some("Jane"),
        organisation   = Some("MDTP"),
        primaryEmail   = "jane.doe@gmail.com",
        slackId        = None,
        username       = "jane.doe",
        githubUsername = None,
        phoneNumber    = None,
        role           = "user",
        teamNames      = Seq.empty[String],
        isDeleted      = false 
      )

      val users = Seq(userOne, userTwo, userThree)

      repository.collection.insertMany(users).toFuture().futureValue

      val res = repository.find().futureValue

      res.length shouldBe 1
      res should contain theSameElementsAs Seq(userOne)

    "find all users that are a member of given team name" in:
      val userOne = User(
        displayName    = Some("Joe Bloggs"),
        familyName     = "Bloggs",
        givenName      = Some("Joe"),
        organisation   = Some("MDTP"),
        primaryEmail   = "joe.bloggs@gmail.com",
        slackId        = None,
        username       = "joe.bloggs",
        githubUsername = None,
        phoneNumber    = None,
        role           = "team-admin",
        teamNames      = Seq("team1"),
        isDeleted      = false 
      )

      val userTwo = User(
        displayName    = Some("John Smith"),
        familyName     = "Smith",
        givenName      = Some("John"),
        organisation   = Some("MDTP"),
        primaryEmail   = "john.smith@gmail.com",
        slackId        = None,
        username       = "john.smith",
        githubUsername = None,
        phoneNumber    = None,
        role           = "team-admin",
        teamNames      = Seq("team1","team2"),
        isDeleted      = false 
      )

      val userThree = User(
        displayName    = Some("Jane Doe"),
        familyName     = "Doe",
        givenName      = Some("Jane"),
        organisation   = Some("MDTP"),
        primaryEmail   = "jane.doe@gmail.com",
        slackId        = None,
        username       = "jane.doe",
        githubUsername = None,
        phoneNumber    = None,
        role           = "team-admin",
        teamNames      = Seq("team1","team2"),
        isDeleted      = false 
      )

      val users = Seq(userOne, userTwo, userThree)

      repository.collection.insertMany(users).toFuture().futureValue

      val res = repository.find(Some("team2")).futureValue

      res.length shouldBe 2
      res should contain theSameElementsAs Seq(userTwo, userThree)

    "find a user by github username" in:
      val userOne = User(
        displayName    = Some("Joe Bloggs"),
        familyName     = "Bloggs",
        givenName      = Some("Joe"),
        organisation   = Some("MDTP"),
        primaryEmail   = "joe.bloggs@gmail.com",
        slackId        = None,
        username       = "joe.bloggs",
        githubUsername = Some("joe-github"),
        phoneNumber    = None,
        role           = "team-admin",
        teamNames      = Seq("team1","team2"),
        isDeleted      = false
      )

      val userTwo = User(
        displayName    = Some("John Smith"),
        familyName     = "Smith",
        givenName      = Some("John"),
        organisation   = Some("MDTP"),
        primaryEmail   = "john.smith@gmail.com",
        slackId        = None,
        username       = "john.smith",
        githubUsername = Some("john-github"),
        phoneNumber    = None,
        role           = "team-admin",
        teamNames      = Seq("team2"),
        isDeleted      = false
      )

      val users = Seq(userOne, userOne.copy(username = "joe.bloggs1"), userTwo)

      repository.collection.insertMany(users).toFuture().futureValue

      val res = repository.find(github = Some("joe-github")).futureValue

      res.length shouldBe 2
      res should contain theSameElementsAs Seq(userOne, userOne.copy(username = "joe.bloggs1"))

  "UsersRepository.findByUsername" should:
    val userOne = User(
      displayName    = Some("Joe Bloggs"),
      familyName     = "Bloggs",
      givenName      = Some("Joe"),
      organisation   = Some("MDTP"),
      primaryEmail   = "joe.bloggs@gmail.com",
      slackId        = None,
      username       = "joe.bloggs",
      githubUsername = None,
      phoneNumber    = None,
      role           = "team-admin",
      teamNames      = Seq("team1", "team2"),
      isDeleted      = false 
    )

    val userTwo = User(
      displayName    = Some("John Smith"),
      familyName     = "Smith",
      givenName      = Some("John"),
      organisation   = Some("MDTP"),
      primaryEmail   = "john.smith@gmail.com",
      slackId        = None,
      username       = "john.smith",
      githubUsername = None,
      phoneNumber    = None,
      role           = "team-admin",
      teamNames      = Seq("team2"),
      isDeleted      = false 
    )

    "return user information for a given username" in:
      repository.collection.insertMany(Seq(userOne, userTwo)).toFuture().futureValue
      
      repository.findByUsername("joe.bloggs").futureValue shouldBe Some(userOne)

    "return None when username is not found" in:
      repository.collection.insertMany(Seq(userOne, userTwo)).toFuture().futureValue

      repository.findByUsername("jane.doe").futureValue shouldBe None

  "UsersRepository.search" should:
    val userOne      = User(
      displayName    = Some("Joe Bloggs"),
      familyName     = "Bloggs",
      givenName      = Some("Joe"),
      organisation   = Some("MDTP"),
      primaryEmail   = "joe.bloggs@gmail.com",
      slackId        = None,
      username       = "joe.bloggs",
      githubUsername = Some("joeGithub"),
      phoneNumber    = None,
      role           = "team-admin",
      teamNames      = Seq("team1", "team2"),
      isDeleted      = false 
    )

    val userTwo      = User(
      displayName    = Some("John Smith"),
      familyName     = "Smith",
      givenName      = Some("John"),
      organisation   = Some("MDTP"),
      primaryEmail   = "john.smith@gmail.com",
      slackId        = None,
      username       = "john.smith",
      githubUsername = None,
      phoneNumber    = None,
      role           = "team-admin",
      teamNames      = Seq("team2"),
      isDeleted      = false 
    )

    val deletedUser      = User(
      displayName    = Some("Deleted User"),
      familyName     = "User",
      givenName      = Some("Deleted"),
      organisation   = Some("MDTP"),
      primaryEmail   = "deleted.user@gmail.com",
      slackId        = None,
      username       = "deleted.user",
      githubUsername = None,
      phoneNumber    = None,
      role           = "team-admin",
      teamNames      = Seq("team2"),
      isDeleted      = true 
    )

    "search for an active user using a search term" in:
      repository.collection.insertMany(Seq(userOne, userTwo, deletedUser)).toFuture().futureValue
      repository.search(Seq("Joe"), includeDeleted = false).futureValue shouldBe List(userOne)

    "search for an active user using multiple search terms" in :
      repository.collection.insertMany(Seq(userOne, userTwo, deletedUser)).toFuture().futureValue
      repository.search(Seq("joeGithub", "Bloggs", "team1"), includeDeleted = false).futureValue shouldBe List(userOne)

    "search for an active user with case insensitive search term" in :
      repository.collection.insertMany(Seq(userOne, userTwo, deletedUser)).toFuture().futureValue
      repository.search(Seq("joe"), includeDeleted = false).futureValue shouldBe List(userOne)

    "return all active users when no there is no search terms" in :
      repository.collection.insertMany(Seq(userOne, userTwo, deletedUser)).toFuture().futureValue
      repository.search(Seq(""), includeDeleted = false).futureValue shouldBe List(userOne, userTwo)

    "return deleted users when specifically requested" in :
      repository.collection.insertMany(Seq(userOne, userTwo, deletedUser)).toFuture().futureValue
      repository.search(Seq(""), includeDeleted = true).futureValue shouldBe List(userOne, userTwo, deletedUser)

end UserRepositorySpec

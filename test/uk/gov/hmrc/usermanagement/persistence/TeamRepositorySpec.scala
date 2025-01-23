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

import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.usermanagement.model.{Member, Team}

import scala.concurrent.ExecutionContext.Implicits.global

class TeamRepositorySpec
  extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[Team]:

  override val repository: TeamsRepository = TeamsRepository(mongoComponent)

  "TeamsRepository.putAll" should:
    "delete the existing teams, and insert new teams into the collection" in new Setup(repository):

      val latestTeams: Seq[Team] = Seq(
        Team(members = Seq(Member(username = "joe.bloggs", displayName = Some("Joe Bloggs"), role = "user", isNonHuman = false)), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None),
      )

      repository.findAll().futureValue shouldNot contain theSameElementsAs latestTeams

      repository.putAll(latestTeams).futureValue

      val res: Seq[Team] = repository.findAll().futureValue

      res.length shouldBe 2
      res        should contain theSameElementsAs latestTeams

  "TeamsRepository.findByTeamName" should:
    "if real name contains a dash" in new Setup(repository):
      val res: Option[Team] = repository.findByTeamName("PlatOps - Support Team").futureValue
      res shouldBe Some(teamWithDashInTeamName)

    "if the search term contains a dash but the real name does not" in new Setup(repository):
      val res: Option[Team] = repository.findByTeamName("DDC-Service").futureValue
      res shouldBe Some(teamNameMadeFromGithubName)

    "if the search term contains a dash and the team does not exist" in new Setup(repository):
      val res: Option[Team] = repository.findByTeamName("Another-Service").futureValue
      res shouldBe None

    "be case insensitive to searches" in new Setup(repository):
      val res: Option[Team] = repository.findByTeamName("TEAM2").futureValue
      res shouldBe Some(team2)
end TeamRepositorySpec

class Setup(repository: TeamsRepository):
  
  import org.mongodb.scala.ObservableFuture

  val teamWithDashInTeamName: Team = 
    Team(
      members           = Seq.empty, 
      teamName          = "PlatOps - Support Team", 
      description       = Some("Example with a dash in the name"), 
      documentation     = None, 
      slack             = None, 
      slackNotification = None
    )
    
  val teamNameMadeFromGithubName: Team = 
    Team(
      members           = Seq.empty, 
      teamName          = "ddc service", 
      description       = Some("Example with a dash in the name"), 
      documentation     = None, 
      slack             = None, 
      slackNotification = None
    )
  val team2: Team = 
    Team(
      members           = Seq.empty, 
      teamName          = "team2", 
      description       = None, 
      documentation     = None, 
      slack             = None, 
      slackNotification = None
    )

  repository.collection.insertMany(Seq(
    Team(
      members           = Seq.empty, 
      teamName          = "team1", 
      description       = None, 
      documentation     = None, 
      slack             = None, 
      slackNotification = None
    ),
    team2,
    teamWithDashInTeamName,
    teamNameMadeFromGithubName
  )).toFuture().futureValue
end Setup

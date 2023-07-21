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
import uk.gov.hmrc.usermanagement.model.{Member, Team}

import scala.concurrent.ExecutionContext.Implicits.global


class TeamRepositorySpec
  extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[Team] {

  override lazy val repository = new TeamsRepository(mongoComponent)

  "TeamsRepository.deleteOldAndInsertNewTeams" should {
    "delete the existing teams, and insert new teams into the collection" in {
      repository.collection.insertMany(Seq(
          Team(members = Seq.empty, teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None)
      )).toFuture().futureValue

      val latestTeams = Seq(
        Team(members = Seq(Member(username = "joe.bloggs", role = "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None),
      )

      repository.deleteOldAndInsertNewTeams(latestTeams).futureValue

      val res = repository.findAll().futureValue
      res.length shouldBe 2

      res should contain theSameElementsAs latestTeams
    }
  }
}


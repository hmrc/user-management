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

  "UsersRepository.insertOrReplaceMany" should {
    "Insert or replace documents" in {
      repository.collection.insertOne(
          Team(members = Seq.empty, teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None)
        ).toFuture().futureValue

      val latestTeams = Seq(
        Team(members = Seq(Member(username = "joe.bloggs", role = "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq.empty, teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
      )

      repository.replaceOrInsertMany(latestTeams).futureValue

      val res = repository.findAll().futureValue
      res.length shouldBe 2

      res should contain theSameElementsAs(latestTeams)
    }

    "Delete many" in {
      repository.collection.insertMany(
        Seq(
          Team(members = Seq(Member(username = "joe.bloggs", role = "user")), teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
          Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None),
        )).toFuture().futureValue

      repository.deleteMany(Seq("team1", "team2")).futureValue

      val res = repository.findAll().futureValue

      res.length shouldBe 1
      res shouldBe Seq(Team(members = Seq.empty, teamName = "team3", description = None, documentation = None, slack = None, slackNotification = None))
    }
  }
}


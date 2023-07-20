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

import org.mongodb.scala.model
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{DeleteOneModel, Filters, IndexModel, IndexOptions, Indexes, ReplaceOneModel, ReplaceOptions}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.usermanagement.model.{Team, User}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsRepository @Inject()(
                                 mongoComponent: MongoComponent,
                               )(implicit
                                 ec            : ExecutionContext
                               ) extends PlayMongoRepository(
  collectionName = "teams",
  mongoComponent = mongoComponent,
  domainFormat   = Team.format,
  indexes        = Seq(
    IndexModel(Indexes.ascending("teamName"),IndexOptions().unique(true).background(true)),
  )
) {
  // No ttl required for this collection - gets updated on a scheduler every 20 minutes, and stale data will be deleted
  // during scheduler run
  override lazy val requiresTtlIndex = false

  def replaceOrInsertMany(teams: Seq[Team]): Future[Unit] = {
    val bulkWrites = teams.map(team =>
      ReplaceOneModel(
        Filters.equal("teamName", team.teamName),
        team,
        ReplaceOptions().upsert(true)
      )
    )
    collection.bulkWrite(bulkWrites).toFuture().map(_ => ())
  }

  def deleteMany(teamNames: Seq[String]): Future[Unit] = {
    val bulkWrites = teamNames.map(tn =>
      DeleteOneModel(
        equal("teamName", tn)
      )
    )

    if(bulkWrites.isEmpty)
      Future.unit
    else
      collection.bulkWrite(bulkWrites)
        .toFuture()
        .map(_ => ())
  }

  def findAll(): Future[Seq[Team]] =
    collection
      .find()
      .toFuture()

  def findAllTeamNames(): Future[Seq[String]] =
    collection
      .find()
      .toFuture()
      .map(_.map(_.teamName))

  def findByTeamName(teamName: String): Future[Option[Team]] =
    collection
      .find(equal("teamName", teamName))
      .headOption()
}


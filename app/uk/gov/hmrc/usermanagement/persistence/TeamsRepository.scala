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

import org.mongodb.scala.model.{Collation, CollationStrength, Filters, IndexModel, IndexOptions, Indexes, ReplaceOneModel, ReplaceOptions, DeleteManyModel, DeleteOptions}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.usermanagement.model.Team
import org.mongodb.scala.model.Filters.{equal, or}
import org.mongodb.scala.ObservableFuture
import uk.gov.hmrc.usermanagement.persistence.TeamsRepository.caseInsensitiveCollation

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsRepository @Inject()(
 mongoComponent: MongoComponent,
)(using
  ExecutionContext
) extends PlayMongoRepository[Team](
  collectionName = "teams",
  mongoComponent = mongoComponent,
  domainFormat   = Team.format,
  indexes        = Seq(
                     IndexModel(Indexes.ascending("teamName"), IndexOptions().unique(true).background(true).collation(caseInsensitiveCollation)),
                   )
):
  // No ttl required for this collection - putAll cleans out stale data
  override lazy val requiresTtlIndex = false

  def putAll(teams: Seq[Team]): Future[Unit] =
    for
      old         <- collection.find().toFuture()
      bulkUpdates =  //upsert any that were not present already
                     teams
                       .filterNot(old.contains)
                       .map: entry =>
                         ReplaceOneModel(
                           Filters.equal("teamName", entry.teamName),
                           entry,
                           ReplaceOptions().upsert(true).collation(caseInsensitiveCollation)
                         ) 
                       ++
                     // delete any that are no longer present
                       old.filterNot(t => teams.exists(_.teamName == t.teamName))
                         .map: entry =>
                           DeleteManyModel(
                             Filters.equal("teamName", entry.teamName),
                             DeleteOptions().collation(caseInsensitiveCollation)
                           )
      _           <- if   bulkUpdates.isEmpty
                     then Future.unit
                     else collection.bulkWrite(bulkUpdates).toFuture().map(_ => ())
    yield ()


  def findAll(): Future[Seq[Team]] =
    collection
      .find()
      .toFuture()

  def findByTeamName(teamName: String): Future[Option[Team]] =
    collection
      .find(
        or(
          equal("teamName", teamName),
          equal("teamName", teamName.replace("-", " "))
        )
      )
      .collation(caseInsensitiveCollation)
      .headOption()
end TeamsRepository

object TeamsRepository:
  private val caseInsensitiveCollation: Collation =
    Collation.builder()
      .locale("en")
      .collationStrength(CollationStrength.SECONDARY)
      .build

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

import org.mongodb.scala.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.usermanagement.model.{Member, Team}
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
      .sort(Sorts.ascending("teamName"))
      .collation(caseInsensitiveCollation)
      .toFuture()

  def updateOne(team: Team): Future[Unit] =
    collection
      .replaceOne(
        filter      = Filters.equal("teamName", team.teamName),
        replacement = team,
        options     = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())

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

  def updateTeamDetails(
    teamName         : String,
    members          : Option[Seq[Member]] = None,
    description      : Option[String]      = None,
    documentation    : Option[String]      = None,
    slack            : Option[String]      = None,
    slackNotification: Option[String]      = None
  ): Future[Unit] =
    val updates = Seq(
      members.map(value => Updates.set("members", value)),
      description.map(value => Updates.set("description", value)),
      documentation.map(value => Updates.set("documentation", value)),
      slack.map(value => Updates.set("slack", value)),
      slackNotification.map(value => Updates.set("slackNotification", value))
    ).flatten

    if updates.isEmpty then Future.unit
    else
      collection.updateOne(
        Filters.equal("teamName", teamName),
        Updates.combine(updates *)
      ).toFuture().map(_ => ())

end TeamsRepository

object TeamsRepository:
  private val caseInsensitiveCollation: Collation =
    Collation.builder()
      .locale("en")
      .collationStrength(CollationStrength.SECONDARY)
      .build

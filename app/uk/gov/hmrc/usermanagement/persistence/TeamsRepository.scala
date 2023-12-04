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

import org.mongodb.scala.model.{Collation, CollationStrength, Filters, IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}
import uk.gov.hmrc.usermanagement.model.Team
import org.mongodb.scala.model.Filters.equal
import uk.gov.hmrc.usermanagement.persistence.TeamsRepository.caseInsensitiveCollation

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsRepository @Inject()(
 override val mongoComponent: MongoComponent,
)(implicit
 ec            : ExecutionContext
) extends PlayMongoRepository[Team](
  collectionName = "teams",
  mongoComponent = mongoComponent,
  domainFormat   = Team.format,
  indexes        = Seq(
    IndexModel(Indexes.ascending("teamName"), IndexOptions().unique(true).background(true).collation(caseInsensitiveCollation)),
  )
) with Transactions {
  // No ttl required for this collection - gets updated on a scheduler every 20 minutes, and stale data will be deleted
  // during scheduler run
  override lazy val requiresTtlIndex = false

  private implicit val tc: TransactionConfiguration = TransactionConfiguration.strict

  def putAll(teams: Seq[Team]): Future[Unit] =
    withSessionAndTransaction (session =>
      for {
        _  <- collection.deleteMany(session, Filters.empty()).toFuture()
        _  <- collection.insertMany(session, teams).toFuture().map(_ => ())
      } yield ()
    )

  def findAll(): Future[Seq[Team]] =
    collection
      .find()
      .toFuture()

  def findByTeamName(teamName: String): Future[Option[Team]] = {
    val convertSlug = teamName.replace("-", " ")
    collection
      .find(equal("teamName", convertSlug))
      .collation(caseInsensitiveCollation)
      .headOption()
  }
}

object TeamsRepository {
  private val caseInsensitiveCollation: Collation =
    Collation.builder()
      .locale("en")
      .collationStrength(CollationStrength.SECONDARY)
      .build
}


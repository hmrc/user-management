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

import org.mongodb.scala.model.Filters.{and, equal, exists, notEqual}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}
import uk.gov.hmrc.usermanagement.model.User

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UsersRepository @Inject()(
 override val mongoComponent: MongoComponent,
)(implicit
 ec            : ExecutionContext
) extends PlayMongoRepository(
  collectionName = "users",
  mongoComponent = mongoComponent,
  domainFormat   = User.format,
  indexes        = Seq(
    IndexModel(Indexes.ascending("username"),      IndexOptions().unique(true).background(true)),
    IndexModel(Indexes.ascending("githubUsername"),IndexOptions().unique(false).background(true))
  )
) with Transactions {

  // No ttl required for this collection - gets updated on a scheduler every 20 minutes, and stale data will be deleted
  // during scheduler run
  override lazy val requiresTtlIndex = false

  private implicit val tc: TransactionConfiguration = TransactionConfiguration.strict

  def putAll(users: Seq[User]): Future[Unit] =
    withSessionAndTransaction (session =>
      for {
        _  <- collection.deleteMany(session, Filters.empty()).toFuture()
        _  <- collection.insertMany(session, users).toFuture().map(_ => ())
      } yield ()
    )

  def find(
    team  : Option[String] = None,
    github: Option[String] = None
  ): Future[Seq[User]] = {

    val filters = Seq(
      // currently we are only interested in surfacing users in teams
      Some(and(exists("teamsAndRoles"), notEqual("teamsAndRoles", Seq.empty))),
      team.map(teamName => equal("teamsAndRoles.teamName", teamName)),
      github.map(username => equal("githubUsername", username))
    ).flatten

    collection.find(Filters.and(filters:_*)).toFuture()
  }

  def findByUsername(username: String): Future[Option[User]] =
    collection
      .find(equal("username", username))
      .headOption()
}


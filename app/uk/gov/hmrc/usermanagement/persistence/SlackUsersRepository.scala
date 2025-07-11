/*
 * Copyright 2025 HM Revenue & Customs
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
import org.mongodb.scala.ObservableFuture
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.usermanagement.model.SlackUser

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlackUsersRepository @Inject()(
  mongoComponent: MongoComponent
)(using
  ExecutionContext
) extends PlayMongoRepository[SlackUser](
  collectionName = "slackUsers",
  mongoComponent = mongoComponent,
  domainFormat   = SlackUser.mongoFormat,
  indexes        = Seq(
                     IndexModel(Indexes.ascending("id")   , IndexOptions().unique(true).background(true)),
                     IndexModel(Indexes.ascending("email"), IndexOptions().sparse(true).background(true))
                   )
):

  // No ttl required for this collection - putAll cleans out stale data
    override lazy val requiresTtlIndex = false

    def putAll(users: Seq[SlackUser]): Future[Unit] =
      for 
        old         <- collection.find().toFuture()
        bulkUpdates =  //upsert any that were not present already
                       users
                         .filterNot(old.contains)
                         .map: entry =>
                           ReplaceOneModel(
                             Filters.equal("id", entry.id),
                             entry,
                             ReplaceOptions().upsert(true)
                           )
                         ++
                       // delete any that are no longer present
                         old.filterNot(u => users.exists(_.id == u.id))
                           .map: entry =>
                             DeleteOneModel(
                               Filters.equal("id", entry.id)
                             )
        _           <- if   (bulkUpdates.isEmpty) Future.unit
                       else collection.bulkWrite(bulkUpdates).toFuture().map(_=> ())
      yield ()

    def findByEmail(email: String): Future[Option[SlackUser]] =
      collection
        .find(
          Filters.equal("email", email)
        )
        .headOption()
        

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

import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.usermanagement.model.UserWithAccess

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserAccessRepository @Inject()(
 mongoComponent: MongoComponent
)(using
  ExecutionContext
) extends PlayMongoRepository(
  collectionName = "userAccess",
  mongoComponent = mongoComponent,
  domainFormat   = UserWithAccess.format,
  indexes        = Seq(
                     IndexModel(
                       Indexes.ascending("username"),
                       IndexOptions().unique(true).background(true)
                     ),
                     IndexModel(
                       Indexes.descending("createdAt"),
                       IndexOptions().name("createdAtIdx").unique(false).expireAfter(43200, TimeUnit.SECONDS) //12hr TTL
                     )
                   )
):

  def put(userWithAccess: UserWithAccess): Future[Unit] =
    collection
      .replaceOne(
        filter      = Filters.equal("username", userWithAccess.username),
        replacement = userWithAccess,
        options     = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())

  def update(
    username: String,
    vpn     : Option[Boolean] = None,
    devTools: Option[Boolean] = None
  ): Future[Unit] =
    val updates = Seq(
      vpn.map(value => Updates.set("access.vpn", value)),
      devTools.map(value => Updates.set("access.devTools", value))
    ).flatten

    if updates.isEmpty then Future.unit
    else
      collection.updateOne(
        Filters.equal("username", username),
        Updates.combine(updates *)
      ).toFuture().map(_ => ())

  def findByUsername(username: String): Future[Option[UserWithAccess]] =
    collection
      .find(Filters.equal("username", username))
      .headOption()

end UserAccessRepository

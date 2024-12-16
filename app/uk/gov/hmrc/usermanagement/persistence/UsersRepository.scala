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

import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, ReplaceOneModel, ReplaceOptions, DeleteOneModel}
import org.mongodb.scala.ObservableFuture
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.usermanagement.model.User

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UsersRepository @Inject()(
 mongoComponent: MongoComponent,
)(using
  ExecutionContext
) extends PlayMongoRepository(
  collectionName = "users",
  mongoComponent = mongoComponent,
  domainFormat   = User.format,
  indexes        = Seq(
                     IndexModel(Indexes.ascending("username"),      IndexOptions().unique(true).background(true)),
                     IndexModel(Indexes.ascending("githubUsername"),IndexOptions().unique(false).background(true))
                   )
):

  // No ttl required for this collection - putAll cleans out stale data
  override lazy val requiresTtlIndex = false

  def putAll(users: Seq[User]): Future[Unit] =
    for
      old         <- collection.find().toFuture()
      bulkUpdates =  //upsert any that were not present already
                     users
                       .filterNot(old.contains)
                       .map: entry =>
                         ReplaceOneModel(
                           Filters.equal("username", entry.username),
                           entry,
                           ReplaceOptions().upsert(true)
                         )
                       ++
                     // delete any that are no longer present
                       old.filterNot(u => users.exists(_.username == u.username))
                         .map: entry =>
                           DeleteOneModel(
                             Filters.equal("username", entry.username)
                           )
      _           <- if   (bulkUpdates.isEmpty) Future.unit
                     else collection.bulkWrite(bulkUpdates).toFuture().map(_=> ())
    yield ()

  def find(
    team  : Option[String] = None,
    github: Option[String] = None
  ): Future[Seq[User]] =
    collection.find(
      Filters.and(
        team.fold(Filters.and(Filters.exists("teamNames"), Filters.notEqual("teamNames", Seq.empty)))(teamName => Filters.equal("teamNames", teamName)),
        github.fold(Filters.empty())(username => Filters.equal("githubUsername", username))
      )
    ).toFuture()

  def search(searchTerms: Seq[String]): Future[Seq[User]] =
    collection.find(
      Filters.and(
        searchTerms.map: term =>
          val regex = s".*$term.*"
          Filters.or(
            Filters.regex("displayName"   , regex, "i")
          , Filters.regex("familyName"    , regex, "i")
          , Filters.regex("givenName"     , regex, "i")
          , Filters.regex("username"      , regex, "i")
          , Filters.regex("githubUsername", regex, "i")
          , Filters.regex("teamNames"     , regex, "i")
          )
        : _*
      )
    ).toFuture()

  def findByUsername(username: String): Future[Option[User]] =
    collection
      .find(Filters.equal("username", username))
      .headOption()
end UsersRepository

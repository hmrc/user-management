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

import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model._
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.usermanagement.model.SlackChannelCache

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlackChannelCacheRepository @Inject()(
                                             mongoComponent: MongoComponent
                                           )(implicit ec: ExecutionContext) extends PlayMongoRepository[SlackChannelCache](
  mongoComponent = mongoComponent,
  collectionName = "slackChannelCache",
  domainFormat   = SlackChannelCache.format,
  indexes        = Seq(
    IndexModel(
      Indexes.ascending("channelName"),
      IndexOptions()
        .name("channelNameIdx")
        .unique(true)
    ),
    IndexModel(
      Indexes.ascending("lastUpdated"),
      IndexOptions()
        .name("lastUpdatedIdx")
        .expireAfter(7, TimeUnit.DAYS)
    )
  ),
  replaceIndexes = true
) with Logging {

  def findByChannelName(channelName: String): Future[Option[SlackChannelCache]] =
    collection
      .find(BsonDocument("channelName" -> channelName))
      .headOption()

  def upsert(channelName: String, isPrivate: Boolean): Future[Unit] = {
    val selector = BsonDocument("channelName" -> channelName)
    val update = Updates.combine(
      Updates.set("isPrivate", isPrivate),
      Updates.set("lastUpdated", Instant.now())
    )
    
    collection
      .updateOne(
        filter  = selector,
        update  = update,
        options = UpdateOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())
  }
}

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

import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.usermanagement.model.SlackChannelCache

import scala.concurrent.ExecutionContext.Implicits.global

class SlackChannelCacheRepositorySpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach
    with DefaultPlayMongoRepositorySupport[SlackChannelCache]:

  override val repository: SlackChannelCacheRepository =
    new SlackChannelCacheRepository(mongoComponent)

  override protected val checkIndexedQueries: Boolean =
    false

  override protected def beforeEach(): Unit =
    super.beforeEach()
    repository.collection.deleteMany(BsonDocument()).toFuture().futureValue

  "SlackChannelCacheRepository.upsert" should :
    "insert a new document when channel does not exist" in :
      val channel = "#engineering"

      // when
      repository.upsert(channel, isPrivate = true).futureValue

      // then
      val foundOpt = repository.findByChannelUrl(channel).futureValue
      foundOpt.isDefined shouldBe true
      val found = foundOpt.value
      found.channelUrl shouldBe channel
      found.isPrivate shouldBe true
    

    "update an existing document when channel already exists" in :
      val channel = "#alerts"

      // given
      repository.upsert(channel, isPrivate = false).futureValue

      // when - flip privacy
      repository.upsert(channel, isPrivate = true).futureValue

      // then
      val foundOpt = repository.findByChannelUrl(channel).futureValue
      foundOpt.isDefined shouldBe true
      val found = foundOpt.value
      found.channelUrl shouldBe channel
      found.isPrivate shouldBe true

      // and only one document exists for that channel (unique index intact)
      val count = repository.collection
        .countDocuments(BsonDocument("channelUrl" -> channel))
        .toFuture()
        .futureValue
      count shouldBe 1L
    
  

  "SlackChannelCacheRepository.findByChannelUrl" should :
    "return None when channel is not cached" in :
      repository.findByChannelUrl("#missing").futureValue shouldBe None
    
  

  "SlackChannelCacheRepository indexes" should :
    "include a unique index on channelUrl and a TTL index on lastUpdated (7 days)" in :
      // Force index creation by touching the collection
      repository.upsert("#idx_check", isPrivate = false).futureValue

      val indexes: Seq[BsonDocument] = repository.collection
        .listIndexes[BsonDocument]()
        .toFuture()
        .futureValue

      val names = indexes.map(doc => doc.getString("name").getValue)
      names should contain ("channelUrlIdx")
      names should contain ("lastUpdatedIdx")

      val lastUpdatedIdx = indexes.find(doc => doc.getString("name").getValue == "lastUpdatedIdx").get

      // TTL is expressed in seconds; 7 days = 604800 seconds
      val ttlSeconds = lastUpdatedIdx.getNumber("expireAfterSeconds").intValue()
      ttlSeconds shouldBe 7 * 24 * 60 * 60
    
  

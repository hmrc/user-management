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
      val channel = "https://hmrcdigital.slack.com/messages/team-engineering"

      // when
      repository.upsert(channel, isPrivate = true).futureValue

      // then
      val foundOpt = repository.findByChannelUrl(channel).futureValue
      foundOpt.isDefined shouldBe true
      val found = foundOpt.value
      found.channelUrl shouldBe channel
      found.isPrivate shouldBe true
    

    "update an existing document when channel already exists" in :
      val channel = "https://hmrcdigital.slack.com/messages/team-alerts"

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
    
  

  "SlackChannelCacheRepository.upsertAll" should :
    "insert multiple new channels when they do not exist" in :
      val channels = Seq(
        ("https://hmrcdigital.slack.com/messages/team-engineering", true),
        ("https://hmrcdigital.slack.com/messages/team-alerts", false),
        ("https://hmrcdigital.slack.com/messages/team-announcements", true)
      )

      // when
      repository.upsertAll(channels).futureValue

      // then
      val engineering = repository.findByChannelUrl("https://hmrcdigital.slack.com/messages/team-engineering").futureValue.value
      engineering.channelUrl shouldBe "https://hmrcdigital.slack.com/messages/team-engineering"
      engineering.isPrivate shouldBe true

      val alerts = repository.findByChannelUrl("https://hmrcdigital.slack.com/messages/team-alerts").futureValue.value
      alerts.channelUrl shouldBe "https://hmrcdigital.slack.com/messages/team-alerts"
      alerts.isPrivate shouldBe false

      val announcements = repository.findByChannelUrl("https://hmrcdigital.slack.com/messages/team-announcements").futureValue.value
      announcements.channelUrl shouldBe "https://hmrcdigital.slack.com/messages/team-announcements"
      announcements.isPrivate shouldBe true
    

    "update existing channels when they already exist" in :
      val channels = Seq(
        ("https://hmrcdigital.slack.com/messages/team-engineering", false),
        ("https://hmrcdigital.slack.com/messages/team-alerts", false)
      )

      // given - insert initial values
      repository.upsertAll(channels).futureValue

      // when - update with different privacy values
      val updatedChannels = Seq(
        ("https://hmrcdigital.slack.com/messages/team-engineering", true),
        ("https://hmrcdigital.slack.com/messages/team-alerts", true)
      )
      repository.upsertAll(updatedChannels).futureValue

      // then
      val engineering = repository.findByChannelUrl("https://hmrcdigital.slack.com/messages/team-engineering").futureValue.value
      engineering.isPrivate shouldBe true

      val alerts = repository.findByChannelUrl("https://hmrcdigital.slack.com/messages/team-alerts").futureValue.value
      alerts.isPrivate shouldBe true

      // and only one document exists for each channel
      val engineeringCount = repository.collection
        .countDocuments(BsonDocument("channelUrl" -> "https://hmrcdigital.slack.com/messages/team-engineering"))
        .toFuture()
        .futureValue
      engineeringCount shouldBe 1L

      val alertsCount = repository.collection
        .countDocuments(BsonDocument("channelUrl" -> "https://hmrcdigital.slack.com/messages/team-alerts"))
        .toFuture()
        .futureValue
      alertsCount shouldBe 1L
    

    "handle empty sequence without error" in :
      repository.upsertAll(Seq.empty).futureValue

      // then - no documents inserted
      val count = repository.collection
        .countDocuments()
        .toFuture()
        .futureValue
      count shouldBe 0L
    

    "handle mixed insert and update operations" in :
      // given - one existing channel
      repository.upsert("https://hmrcdigital.slack.com/messages/team-existing", isPrivate = false).futureValue

      // when - bulk operation with both new and existing channels
      val channels = Seq(
        ("https://hmrcdigital.slack.com/messages/team-existing", true),      // update
        ("https://hmrcdigital.slack.com/messages/team-new-channel", false)   // insert
      )
      repository.upsertAll(channels).futureValue

      // then
      val existing = repository.findByChannelUrl("https://hmrcdigital.slack.com/messages/team-existing").futureValue.value
      existing.isPrivate shouldBe true

      val newChannel = repository.findByChannelUrl("https://hmrcdigital.slack.com/messages/team-new-channel").futureValue.value
      newChannel.channelUrl shouldBe "https://hmrcdigital.slack.com/messages/team-new-channel"
      newChannel.isPrivate shouldBe false

      // and total documents
      val count = repository.collection
        .countDocuments()
        .toFuture()
        .futureValue
      count shouldBe 2L
    
  

  "SlackChannelCacheRepository.findByChannelUrl" should :
    "return None when channel is not cached" in :
      repository.findByChannelUrl("https://hmrcdigital.slack.com/messages/team-missing").futureValue shouldBe None
    
  

  "SlackChannelCacheRepository indexes" should :
    "include a unique index on channelUrl and a TTL index on lastUpdated (7 days)" in :
      // Force index creation by touching the collection
      repository.upsert("https://hmrcdigital.slack.com/messages/team-idx_check", isPrivate = false).futureValue

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
    
  

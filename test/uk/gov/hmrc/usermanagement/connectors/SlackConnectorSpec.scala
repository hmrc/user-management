/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.usermanagement.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.usermanagement.model.SlackUser
import uk.gov.hmrc.usermanagement.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class SlackConnectorSpec
  extends UnitSpec
    with ScalaFutures
    with IntegrationPatience
    with WireMockSupport
    with HttpClientV2Support:

  private given HeaderCarrier = new HeaderCarrier()
  private given ActorSystem   = ActorSystem("test")

  private val config = ConfigFactory.parseString(
    s"""
       |slack.apiUrl = "$wireMockUrl"
       |slack.token  = token
       |slack.limit  = 2
       |slack.requestThrottle = 4.seconds
       |""".stripMargin
  )

  private val connector: SlackConnector =
    new SlackConnector(
      httpClientV2,
      new Configuration(config)
    )

  "getAllSlackUsers" should:
    "correctly read in pages and combine them" in:
      stubFor(
        get(urlEqualTo(s"/users.list?limit=2&cursor="))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                s"""
                   |{
                   |  "members": [
                   |    {
                   |      "id": "id_A",
                   |      "name": "user.one",
                   |      "is_bot": false,
                   |      "deleted": false,
                   |      "profile": {}
                   |    },
                   |    {
                   |      "id": "id_B",
                   |      "name": "user.two",
                   |      "is_bot": false,
                   |      "deleted": false,
                   |      "profile": {
                   |        "email": "B@gmail.com"
                   |      }
                   |    }
                   |  ],
                   |  "response_metadata": {
                   |    "next_cursor": "page_2"
                   |  }
                   |}
                   |""".stripMargin
              )
          )
      )

      stubFor(
        get(urlEqualTo(s"/users.list?limit=2&cursor=page_2"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                s"""
                   |{
                   |  "members": [
                   |    {
                   |      "id": "id_C",
                   |      "name": "user.thr",
                   |      "is_bot": false,
                   |      "deleted": false,
                   |      "profile": {
                   |        "email": "C@gmail.com"
                   |      }
                   |    }
                   |  ],
                   |  "response_metadata": {
                   |    "next_cursor": ""
                   |  }
                   |}
                   |""".stripMargin
              )
          )
      )
      connector.getAllSlackUsers().futureValue shouldBe Seq(
        SlackUser(email = None               , id = "id_A", name = "user.one", isBot = false, isDeleted = false),
        SlackUser(email = Some("B@gmail.com"), id = "id_B", name = "user.two", isBot = false, isDeleted = false),
        SlackUser(email = Some("C@gmail.com"), id = "id_C", name = "user.thr", isBot = false, isDeleted = false)
      )

  "lookupUserByEmail" should:
    "return a SlackUser when found" in:
      stubFor(
        get(urlEqualTo("/users.lookupByEmail?email=test@example.com"))
          .willReturn(okJson(
            s"""
               |{
               |  "ok": true,
               |  "user": { "id": "U123", "name": "test.user", "is_bot": false, "deleted": false, "profile": { "email": "test@example.com" } }
               |}
               |""".stripMargin))
      )

      val result = connector.lookupUserByEmail("test@example.com").futureValue
      result.value shouldBe SlackUser(Some("test@example.com"), "U123", "test.user", isBot = false, isDeleted = false)

    "return None when Slack reports ok=false" in:
      stubFor(
        get(urlEqualTo("/users.lookupByEmail?email=missing@example.com"))
          .willReturn(okJson(
            """{ "ok": false, "error": "users_not_found" }"""
          ))
      )

      connector.lookupUserByEmail("missing@example.com").futureValue shouldBe None

  "listAllChannels" should:
    "page through results correctly" in:
      stubFor(
        get(urlEqualTo("/conversations.list?limit=2&cursor=&exclude_archived=true&types=public_channel,private_channel"))
          .willReturn(okJson(
            s"""
               |{
               |  "channels": [ { "id": "C1", "name": "team-alpha" } ],
               |  "response_metadata": { "next_cursor": "cursor2" }
               |}
               |""".stripMargin))
      )

      stubFor(
        get(urlEqualTo("/conversations.list?limit=2&cursor=cursor2&exclude_archived=true&types=public_channel,private_channel"))
          .willReturn(okJson(
            s"""
               |{
               |  "channels": [ { "id": "C2", "name": "team-beta" } ],
               |  "response_metadata": { "next_cursor": "" }
               |}
               |""".stripMargin))
      )

      connector.listAllChannels().futureValue shouldBe Seq(
        SlackChannel("C1", "team-alpha"),
        SlackChannel("C2", "team-beta")
      )

  "createChannel" should:
    "return the created channel when successful" in:
      stubFor(
        post(urlEqualTo("/conversations.create"))
          .withRequestBody(equalToJson("""{"name":"team-gamma"}"""))
          .willReturn(okJson(
            s"""
               |{ "ok": true, "channel": { "id": "C123", "name": "team-gamma" } }
               |""".stripMargin))
      )

      connector.createChannel("team-gamma").futureValue shouldBe Some(SlackChannel("C123", "team-gamma"))

    "return None when creation fails" in:
      stubFor(
        post(urlEqualTo("/conversations.create"))
          .willReturn(okJson("""{ "ok": false, "error": "name_taken" }"""))
      )

      connector.createChannel("team-delta").futureValue shouldBe None

  "inviteUsersToChannel" should:
    "POST to Slack with correct channel and users" in:
      stubFor(
        post(urlEqualTo("/conversations.invite"))
          .withRequestBody(equalToJson("""{"channel":"C111","users":"U1,U2"}"""))
          .willReturn(okJson("""{"ok": true}"""))
      )

      connector.inviteUsersToChannel("C111", Seq("U1", "U2")).futureValue
      verify(
        postRequestedFor(urlEqualTo("/conversations.invite"))
          .withRequestBody(equalToJson("""{"channel":"C111","users":"U1,U2"}"""))
      )

    "do nothing when no user IDs provided" in:
      connector.inviteUsersToChannel("C111", Seq.empty).futureValue
      verify(0, postRequestedFor(urlMatching(".*conversations.invite.*")))

  "listChannelMembers" should:
    "return member IDs from Slack" in:
      stubFor(
        get(urlEqualTo("/conversations.members?channel=CXYZ"))
          .willReturn(okJson(
            """{ "members": ["U1","U2","U3"] }"""
          ))
      )

      connector.listChannelMembers("CXYZ").futureValue shouldBe Seq("U1", "U2", "U3")

end SlackConnectorSpec

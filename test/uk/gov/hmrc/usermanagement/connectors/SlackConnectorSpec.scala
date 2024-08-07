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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.typesafe.config.ConfigFactory
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

  private val config = ConfigFactory.parseString(
    s"""
       |slack.apiUrl = "$wireMockUrl"
       |slack.token  = token
       |slack.limit  = 2
       |""".stripMargin
  )

  private val connector: SlackConnector =
    new SlackConnector(
      httpClientV2,
      new Configuration(config)
    )

  "getAllSlackIDs" should:
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
                   |      "profile": {}
                   |    },
                   |    {
                   |      "id": "id_B",
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
        SlackUser(email = None               , id = "id_A"),
        SlackUser(email = Some("B@gmail.com"), id = "id_B"),
        SlackUser(email = Some("C@gmail.com"), id = "id_C"),
      )
end SlackConnectorSpec


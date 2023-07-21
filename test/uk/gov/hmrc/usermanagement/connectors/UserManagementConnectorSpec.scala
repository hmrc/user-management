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

package uk.gov.hmrc.usermanagement.connectors

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, stubFor, urlEqualTo}
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.{HeaderCarrier, JsValidationException, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.usermanagement.model.{Member, Team, User}
import uk.gov.hmrc.usermanagement.test.UnitSpec

class UserManagementConnectorSpec
  extends UnitSpec
    with ScalaFutures
    with WireMockSupport
    with IntegrationPatience
    with HttpClientV2Support
    with EitherValues
    with BeforeAndAfterEach
    with GuiceOneServerPerSuite
{

    override lazy val wireMockRootDirectory = "test/resources"

    implicit val hc = HeaderCarrier()

    override def fakeApplication(): Application =
      new GuiceApplicationBuilder()
        .configure(
          "microservice.services.user-management.port"       -> wireMockPort,
          "microservice.services.user-management.host"       -> wireMockHost,
          "play.http.requestHandler"                         -> "play.api.http.DefaultHttpRequestHandler",
          "metrics.jvm"                                      -> false,
          "ump.auth.username"                                -> "user",
          "ump.auth.password"                                -> "pass",
          "ump.auth.tokenTTL"                                -> "1 hour",
          "ump.loginUrl"                                     -> s"$wireMockUrl/v1/login",
          "ump.baseUrl"                                      -> s"$wireMockUrl",
        )
        .build()


    private val userManagementConnector: UserManagementConnector =
      app.injector.instanceOf[UserManagementConnector]

    "getAllUsers" when {
      "parsing a valid response" should {
        "return a sequence of Users" in new Setup {
          stubFor(
            WireMock.get(urlEqualTo("/v2/organisations/users"))
              .willReturn(
                aResponse()
                  .withStatus(200)
                  .withBodyFile("valid-users.json")
              )
          )

          val res = userManagementConnector.getAllUsers().futureValue

          res should contain theSameElementsAs Seq(
              User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = Some("https://github.com/hmrc"), phoneNumber = Some("12345678912"), teamsAndRoles = None),
              User(displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = None, primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, teamsAndRoles = None)
            )
        }
      }

      "parsing an invalid JSON response" should {
        "throw a JSValidationException" in new Setup {
          stubFor(
            WireMock.get(urlEqualTo("/v2/organisations/users"))
              .willReturn(
                aResponse()
                  .withStatus(200)
                  .withBodyFile("invalid-users.json")
              )
          )

          val res = userManagementConnector.getAllUsers().failed.futureValue
          res shouldBe a [JsValidationException]
        }
      }

      "it receives a non 200 status code response" should {
        "return an UpstreamErrorResponse" in new Setup {
          stubFor(
            WireMock.get(urlEqualTo("/v2/organisations/users"))
              .willReturn(
                aResponse()
                  .withStatus(500)
              )
          )

          val res = userManagementConnector.getAllUsers().failed.futureValue
          res shouldBe a [UpstreamErrorResponse]
        }
      }

      "the response contains non-human users" should {
        "filter out any non-human users" in new Setup {
          stubFor(
            WireMock.get(urlEqualTo("/v2/organisations/users"))
              .willReturn(
                aResponse()
                  .withStatus(200)
                  .withBodyFile("non-human-users.json")
              )
          )

          val res = userManagementConnector.getAllUsers().futureValue

          res.length shouldBe 2
          res should contain theSameElementsAs Seq(
            User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = Some("https://github.com/hmrc"), phoneNumber = Some("12345678912"), teamsAndRoles = None),
            User(displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = None, primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, teamsAndRoles = None)
          )
        }
      }
    }

  "getAllTeams" when {
    "parsing a valid response" should {
      "return a sequence of Teams" in new Setup {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("valid-teams.json")
            )
        )

        val res = userManagementConnector.getAllTeams().futureValue

        res should contain theSameElementsAs Seq(
          Team(members = Seq(), teamName = "PlatOps", description = Some("A great team"), documentation = Some("Confluence"), slack = Some("https://slackchannel.com"), slackNotification = Some("https://slackchannel2.com")),
          Team(members = Seq(), teamName = "Other Team", description = None, documentation = None, slack = None, slackNotification = None)
        )
      }
    }


    "parsing an invalid JSON response" should {
      "throw a JSValidationException" in new Setup {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("invalid-teams.json")
            )
        )

        val res = userManagementConnector.getAllTeams().failed.futureValue
        res shouldBe a [JsValidationException]
      }
    }

    "it receives a non 200 status code response" should {
      "return an UpstreamErrorResponse" in new Setup{
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams"))
            .willReturn(
              aResponse()
                .withStatus(500)
            )
        )

        val res = userManagementConnector.getAllTeams().failed.futureValue
        res shouldBe a [UpstreamErrorResponse]
      }
    }
  }

  "getMembersForTeam" when {
    "parsing a valid response" should {
      "return a Team" in new Setup {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("valid-members-of-team.json")
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").futureValue

        res shouldBe Some(Team(
          members = Seq(Member(username = "joe.bloggs", role = "team_admin"), Member(username = "jane.doe", role = "user")),
          teamName = "PlatOps",
          description = None,
          documentation = None,
          slack = Some("https://slack.com"),
          slackNotification = None
        ))

      }
    }

    "parsing an invalid JSON response" should {
      "throw a JSValidationException" in new Setup {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("invalid-members-of-team.json")
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").failed.futureValue
        res shouldBe a [JsValidationException]
      }
    }

    "it receives a 404 status code response" should {
      "recover with a None" in new Setup {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(404)
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").futureValue
        res shouldBe None
      }
    }

    "it receives a 422 status code response" should {
      "recover with a None" in new Setup {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(422)
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").futureValue
        res shouldBe None
      }
    }

    "it receives any other non 200 status code response" should {
      "return an UpstreamErrorResponse" in new Setup {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(500)
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").failed.futureValue
        res shouldBe a [UpstreamErrorResponse]
      }
    }

    "the response contains non-human members" should {
      "filter out the non-humans" in new Setup {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("non-human-members-of-team.json")
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").futureValue

        res shouldBe Some(Team(
          members = Seq(Member(username = "joe.bloggs", role = "team_admin"), Member(username = "jane.doe", role = "user")),
          teamName = "PlatOps",
          description = None,
          documentation = None,
          slack = Some("https://slack.com"),
          slackNotification = None
        ))
      }
    }
  }

}

trait Setup {
  stubFor(
    WireMock.post(urlEqualTo("/v1/login"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""{"Token":"token","uid": "uid"}""")
      )
  )
}

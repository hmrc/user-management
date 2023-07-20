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

import akka.Done
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, stubFor, urlEqualTo}
import org.scalatest.EitherValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import uk.gov.hmrc.http.{HeaderCarrier, JsValidationException, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.usermanagement.config.{UserManagementAuthConfig, UserManagementPortalConfig}
import uk.gov.hmrc.usermanagement.model.{Team, TeamMember, User}
import uk.gov.hmrc.usermanagement.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

class UserManagementConnectorSpec
  extends UnitSpec
    with ScalaFutures
    with WireMockSupport
    with IntegrationPatience
    with HttpClientV2Support
    with EitherValues
{

    override lazy val wireMockRootDirectory = "test/resources"

    implicit val hc = HeaderCarrier()

    val cache = new AsyncCacheApi {
      override def set(key: String, value: Any, expiration: Duration): Future[Done] = ???
      override def remove(key: String): Future[Done] = ???
      override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: =>Future[A])(implicit evidence$1: ClassTag[A]): Future[A] = ???
      override def get[T](key: String)(implicit evidence$2: ClassTag[T]): Future[Option[T]] = ???
      override def removeAll(): Future[Done] = ???
    }

    val servicesConfig = new ServicesConfig(Configuration("microservice.services.user-management.url" -> wireMockUrl))
    val authConfig     = new UserManagementAuthConfig(Configuration("ump.auth.enabled" -> false))
    val umpConfig      = new UserManagementPortalConfig(servicesConfig)

    lazy val userManagementConnector = new UserManagementConnector(umpConfig, httpClientV2, authConfig, cache)

    "getAllUsers" when {
      "parsing a valid response" should {
        "return a sequence of Users" in {
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
        "throw a JSValidationException" in {
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
        "return an UpstreamErrorResponse" in {
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
    }

  "getAllTeams" when {
    "parsing a valid response" should {
      "return a sequence of Teams" in {
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
      "throw a JSValidationException" in {
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
      "return an UpstreamErrorResponse" in {
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
      "return a sequence of TeamMembers" in {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("valid-members-of-team.json")
            )
        )

        val res = userManagementConnector.getMembersForTeam("PlatOps").futureValue

        res.get should contain theSameElementsAs Seq(
          TeamMember(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = Some("https://github.com/hmrc"), phoneNumber = Some("12345678912"), role = "team_admin"),
          TeamMember(displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = None, primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, role = "user")
        )
      }
    }

    "parsing an invalid JSON response" should {
      "throw a JSValidationException" in {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("invalid-members-of-team.json")
            )
        )

        val res = userManagementConnector.getMembersForTeam("PlatOps").failed.futureValue
        res shouldBe a [JsValidationException]
      }
    }

    "it receives a 404 status code response" should {
      "recover with a None" in {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(404)
            )
        )

        val res = userManagementConnector.getMembersForTeam("PlatOps").futureValue
        res shouldBe None
      }
    }

    "it receives a 422 status code response" should {
      "recover with a None" in {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(422)
            )
        )

        val res = userManagementConnector.getMembersForTeam("PlatOps").futureValue
        res shouldBe None
      }
    }

    "it receives any other non 200 status code response" should {
      "return an UpstreamErrorResponse" in {
        stubFor(
          WireMock.get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(500)
            )
        )

        val res = userManagementConnector.getMembersForTeam("PlatOps").failed.futureValue
        res shouldBe a [UpstreamErrorResponse]
      }
    }
  }

}

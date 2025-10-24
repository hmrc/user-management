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

package uk.gov.hmrc.usermanagement.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.usermanagement.model.TeamName
import uk.gov.hmrc.usermanagement.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class TeamsAndRepositoriesConnectorSpec
  extends UnitSpec
    with ScalaFutures
    with IntegrationPatience
    with WireMockSupport
    with HttpClientV2Support:

  private given HeaderCarrier = HeaderCarrier()

  private val config = ConfigFactory.parseString(
    s"""
       |microservice.services.teams-and-repositories.port = ${wireMockPort}
       |microservice.services.teams-and-repositories.host = "localhost"
       |""".stripMargin
  )

  private val connector = TeamsAndRepositoriesConnector(
    httpClientV2,
    ServicesConfig(Configuration(config))
  )

  "allTeams" should:
    "return a list of teams" in:
      val responseJson =
        s"""
           |[
           |  { "name": "A-Team", "lastActiveDate": "2024-10-11T14:31:59Z", "repos": [] },
           |  { "name": "B-Team", "lastActiveDate": "2024-09-01T09:00:00Z", "repos": [] }
           |]
           |""".stripMargin

      stubFor(
        get(urlEqualTo("/api/v2/teams"))
          .willReturn(okJson(responseJson))
      )

      val result = connector.allTeams()(using HeaderCarrier()).futureValue
      result should have size 2
      result.map(_.name) shouldBe Seq(TeamName("A-Team"), TeamName("B-Team"))

    "filter by team name when provided" in:
      val teamName = TeamName("PlatOps")

      stubFor(
        get(urlEqualTo(s"/api/v2/teams?name=${teamName.asString}"))
          .willReturn(okJson(
            s"""
               |[
               |  { "name": "PlatOps", "lastActiveDate": "2025-01-01T00:00:00Z", "repos": [] }
               |]
               |""".stripMargin))
      )

      val result = connector.allTeams(Some(teamName)).futureValue
      result should have size 1
      result.head.name shouldBe TeamName("PlatOps")

  "allRepositories" should:
    "return a sorted list of repositories" in:
      stubFor(
        get(urlEqualTo("/api/v2/repositories"))
          .willReturn(okJson(
            s"""
               |[
               |  { "name": "b-repo", "description": "desc B", "url": "urlB", "createdDate": "2024-05-10T10:00:00Z",
               |    "lastActiveDate": "2024-05-20T10:00:00Z", "isPrivate": false, "repoType": "Library",
               |    "tags": [], "owningTeams": ["Team1"], "language": "Scala", "isArchived": false,
               |    "defaultBranch": "main", "branchProtection": {}, "isDeprecated": false, "teamNames": ["Team1"],
               |    "repositoryYamlText": ""
               |  },
               |  { "name": "a-repo", "description": "desc A", "url": "urlA", "createdDate": "2024-04-10T10:00:00Z",
               |    "lastActiveDate": "2024-04-20T10:00:00Z", "isPrivate": false, "repoType": "Service",
               |    "tags": [], "owningTeams": ["Team2"], "language": "Scala", "isArchived": false,
               |    "defaultBranch": "main", "branchProtection": {}, "isDeprecated": false, "teamNames": ["Team2"],
               |    "repositoryYamlText": ""
               |  }
               |]
               |""".stripMargin))
      )

      val result = connector.allRepositories().futureValue
      result.map(_.name) shouldBe Seq("a-repo", "b-repo") // sorted alphabetically

    "filter by parameters correctly" in:
      val team = TeamName("PlatOps")

      stubFor(
        get(urlEqualTo(s"/api/v2/repositories?name=foo&owningTeam=${team.asString}&archived=false"))
          .willReturn(okJson("[]"))
      )

      connector.allRepositories(name = Some("foo"), team = Some(team), archived = Some(false)).futureValue shouldBe Seq.empty

end TeamsAndRepositoriesConnectorSpec

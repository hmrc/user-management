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

package uk.gov.hmrc.usermanagement

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, stubFor, urlPathMatching}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.usermanagement.config.UserManagementPortalConfig
import uk.gov.hmrc.usermanagement.model.{Team, TeamAndRole, User}
import uk.gov.hmrc.usermanagement.persistence.{TeamsRepository, UsersRepository}

class DataRefreshIntegrationSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with Eventually
     with IntegrationPatience
     with GuiceOneServerPerSuite
     with WireMockSupport
     with CleanMongoCollectionSupport {

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(Map(
        "metrics.enabled"                           -> false,
        "mongodb.uri"                               -> mongoUri,
        "scheduler.dataRefresh.enabled"             -> true,
        "microservice.services.user-management.url" -> wireMockUrl,
        "ump.auth.enabled"                          -> false
      ))
      .build()

  val usersCollection = app.injector.instanceOf[UsersRepository]
  val teamsCollection = app.injector.instanceOf[TeamsRepository]

  val userManagementConfig = app.injector.instanceOf[UserManagementPortalConfig]
  val userManagementUrl = userManagementConfig.userManagementBaseUrl

  "dataRefreshService" should {
    "Filter out non-human data, delete stale data from the collection, and insert/replace the remaining data" in {
      //1. Prefill the user and teams collections with some existing data
      usersCollection.collection.insertMany(Seq(
        User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joe"), organisation = None, primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, teamsAndRoles = None),
        User(displayName = Some("Jane Doe"), familyName = "Doe", givenName = Some("Jane"), organisation = Some("MDTP"), primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, teamsAndRoles = None),
      ))

      teamsCollection.collection.insertMany(Seq(
        Team(members = Seq.empty, teamName = "team1", description = None, documentation = None, slack = None, slackNotification = None),
        Team(members = Seq.empty, teamName = "team2", description = None, documentation = None, slack = None, slackNotification = None),
      ))

      println("CHECK WHETHER WIREMOCK IS RUNNING")
      println(wireMockServer.isRunning)

      //2. Set UMP stub responses
      stubFor(WireMock.get(urlPathMatching("/v2/organisations/users"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody((DataRefreshStubResponses.users))))

      stubFor(WireMock.get(urlPathMatching("/v2/organisations/teams"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody((DataRefreshStubResponses.teams))))

      stubFor(WireMock.get(urlPathMatching("/v2/organisations/teams/team1/members"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody((DataRefreshStubResponses.team1))))

      stubFor(WireMock.get(urlPathMatching("/v2/organisations/teams/team3/members"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody((DataRefreshStubResponses.team3))))



      //3. Provide implicit vals for our expected results
      implicit val uf = User.format
      implicit val tf = Team.format

      //4. It takes time for the scheduler to autostart, and run through the full process
      eventually {
        val usersRes = usersCollection.findAll().futureValue
        val teamsRes = teamsCollection.findAll().futureValue

        //TEST
        //Users collection should not have inserted the non-human user received from UMP,
        // should replace Joe.bloggs with new document which has a different given name
        // should delete jane.doe as she was not returned in the list of users by UMP.
        // should insert the new user

        usersRes.length shouldBe 2
        usersRes should contain theSameElementsAs(Seq(
          User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = Some("Joseph"), organisation = None, primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = None, phoneNumber = None, teamsAndRoles = Some(Seq(TeamAndRole("team1", "team-admin")))),
          User(displayName = Some("New User"), familyName = "User", givenName = Some("New"), organisation = None, primaryEmail = "new.user@gmail.com", username = "new.user", github = None, phoneNumber = None, teamsAndRoles = Some(Seq(TeamAndRole("team3", "user"))))
        ))

      }
    }
  }
}

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

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.*
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, JsValidationException, UpstreamErrorResponse}
import uk.gov.hmrc.usermanagement.model.*

class UmpConnectorSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with ScalaFutures
     with WireMockSupport
     with IntegrationPatience
     with HttpClientV2Support
     with GuiceOneServerPerSuite:

  private given HeaderCarrier = HeaderCarrier()

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.user-management.port" -> wireMockPort,
        "microservice.services.user-management.host" -> wireMockHost,
        "microservice.services.internal-auth.port"   -> wireMockPort,
        "microservice.services.internal-auth.host"   -> wireMockHost,
        "play.http.requestHandler"                   -> "play.api.http.DefaultHttpRequestHandler",
        "metrics.jvm"                                -> false,
        "ump.auth.username"                          -> "user",
        "ump.auth.password"                          -> "pass",
        "ump.auth.tokenTTL"                          -> "1 hour",
        "ump.loginUrl"                               -> s"$wireMockUrl/v1/login",
        "ump.baseUrl"                                -> s"$wireMockUrl",
      )
      .build()

  private val userManagementConnector: UmpConnector =
    app.injector.instanceOf[UmpConnector]

  "getAllUsers" when:
    "parsing a valid response" should:
      "return a sequence of Users" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/users?includeDeleted=true"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("valid-users.json")
            )
        )

        val res = userManagementConnector.getAllUsers().futureValue

        res should contain theSameElementsAs Seq(
            User(displayName = Some("Joe Bloggs"), familyName = "Bloggs" , givenName = Some("Joe")    , organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", slackId = None, username = "joe.bloggs", githubUsername = Some("hmrc"), phoneNumber = Some("12345678912"), role="user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = false),
            User(displayName = Some("Jane Doe")  , familyName = "Doe"    , givenName = Some("Jane")   , organisation = None        , primaryEmail = "jane.doe@gmail.com"  , slackId = None, username = "jane.doe", githubUsername = None, phoneNumber = None, role="user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = false),
            User(displayName = Some("service")   , familyName = "service", givenName = Some("service"), organisation = None        , primaryEmail = "service@gmail.com"   , slackId = None, username = "service-account", githubUsername = None, phoneNumber = None, role="user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = true),
            User(displayName = Some("platops")   , familyName = "plat"   , givenName = Some("ops")    , organisation = None        , primaryEmail = "platops@gmail.com"   , slackId = None, username = "PLaToPs", githubUsername = None, phoneNumber = None, role="user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = true),
            User(displayName = Some("build")     , familyName = "b"      , givenName = Some("b")      , organisation = None        , primaryEmail = "b@gmail.com"   , slackId = None, username = "BUILD", githubUsername = None, phoneNumber = None, role="user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = true),
            User(displayName = Some("deploy")    , familyName = "dep"    , givenName = Some("d")      , organisation = None        , primaryEmail = "d@gmail.com"         , slackId = None, username = "DePlOy", githubUsername = None, phoneNumber = None, role="user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = true),
            User(displayName = Some("ddcops")    , familyName = "d"      , givenName = Some("d")      , organisation = None        , primaryEmail = "d@gmail.com"         , slackId = None, username = "ddcops_", githubUsername = None, phoneNumber = None, role="user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = true),
            User(displayName = Some("deskpro")   , familyName = "d"      , givenName = Some("d")      , organisation = None        , primaryEmail = "d@gmail.com"         , slackId = None, username = "Deskpro", githubUsername = None, phoneNumber = None, role="user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = true),
            User(displayName = Some("platSEC")   , familyName = "p"      , givenName = Some("p")      , organisation = None        , primaryEmail = "p@gmail.com"         , slackId = None, username = "platSEC", githubUsername = None, phoneNumber = None, role="user", teamNames = Seq.empty[String], isDeleted = false, isNonHuman = true),
          )

    "parsing an invalid JSON response" should:
      "throw a JSValidationException" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/users?includeDeleted=true"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("invalid-users.json")
            )
        )

        val res = userManagementConnector.getAllUsers().failed.futureValue
        res shouldBe a [JsValidationException]

    "it receives a non 200 status code response" should:
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/users?includeDeleted=true"))
            .willReturn(
              aResponse()
                .withStatus(500)
            )
        )
        val res = userManagementConnector.getAllUsers().failed.futureValue
        res shouldBe a [UpstreamErrorResponse]

  "createUser" when :
    "parsing a valid response" should :
      "return unit" in new Setup:
        stubFor(
          post(urlEqualTo("/v2/user_requests/users/none"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody("""{"status": "OK"}""")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res: Unit =
          userManagementConnector.createUser(createUserRequest).futureValue

        res shouldBe ()

    "it receives a non 2xx status code response" should :
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          post(urlEqualTo("/v2/user_requests/users/none"))
            .willReturn(
              aResponse()
                .withStatus(500)
                .withBody("""{"message": "Error creating user: One of username or displayName must be set"}""")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res: Throwable =
          userManagementConnector.createUser(createUserRequest).failed.futureValue

        res shouldBe a [UpstreamErrorResponse]

    "it receives a 401 response from internal auth" should :
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(401)
            )
        )

        val res: Throwable =
          userManagementConnector.createUser(createUserRequest).failed.futureValue

        res shouldBe a [UpstreamErrorResponse]

  "editUserDetails" when :
    "parsing a valid response" should :
      "return unit" in new Setup:
        stubFor(
          put(urlEqualTo("/v2/organisations/users/joe.bloggs/displayName"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody("""{"status": "OK"}""")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res: Unit =
          userManagementConnector.editUserDetails(editUserDetailsRequest).futureValue

        res shouldBe()

    "it receives a non 2xx status code response" should :
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          put(urlEqualTo("/v2/organisations/users/joe.bloggs/displayName"))
            .willReturn(
              aResponse()
                .withStatus(403)
                .withBody("""{"reason": "Forbidden"}""")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res: Throwable =
          userManagementConnector.editUserDetails(editUserDetailsRequest).failed.futureValue

        res shouldBe a[UpstreamErrorResponse]

    "it receives a 401 response from internal auth" should :
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(401)
            )
        )

        val res: Throwable =
          userManagementConnector.editUserDetails(editUserDetailsRequest).failed.futureValue

        res shouldBe a[UpstreamErrorResponse]

  "resetUserLdapPassword" when :
    "parsing a valid response" should :
      "return a ticket number" in new Setup:
        stubFor(
          put(urlEqualTo(s"/v2/organisations/users/$username/password"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody("""{"ticket_number": "some-unique-ticket-id"}""")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res: JsValue =
          userManagementConnector.resetUserLdapPassword(resetLdapPassword).futureValue

        res shouldBe Json.parse("""{"ticket_number": "some-unique-ticket-id"}""")

    "it receives a non 2xx status code response" should :
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          put(urlEqualTo(s"/v2/organisations/users/$username/password"))
            .willReturn(
              aResponse()
                .withStatus(403)
                .withBody("""{"error": "invalid email given"}""")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res: Throwable =
          userManagementConnector.resetUserLdapPassword(resetLdapPassword).failed.futureValue

        res shouldBe a[UpstreamErrorResponse]

    "it receives a 401 response from internal auth" should :
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(401)
            )
        )

        val res: Throwable =
          userManagementConnector.resetUserLdapPassword(resetLdapPassword).failed.futureValue

        res shouldBe a[UpstreamErrorResponse]

  "editUserAccess" when :
    "parsing a valid response" should :
      "return unit" in new Setup:
        stubFor(
          post(urlEqualTo(s"/v2/user_requests/users/$username"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody("""{"status": "OK"}""")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res: Unit =
          userManagementConnector.editUserAccess(editUserAccessRequest).futureValue

        res shouldBe()

    "it receives a non 2xx status code response" should :
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          post(urlEqualTo(s"/v2/user_requests/users/$username"))
            .willReturn(
              aResponse()
                .withStatus(403)
                .withBody("""{"message": "Not authorised for operation"}""")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res: Throwable =
          userManagementConnector.editUserAccess(editUserAccessRequest).failed.futureValue

        res shouldBe a[UpstreamErrorResponse]

    "it receives a 401 response from internal auth" should :
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(401)
            )
        )

        val res: Throwable =
          userManagementConnector.editUserAccess(editUserAccessRequest).failed.futureValue

        res shouldBe a[UpstreamErrorResponse]

  "getUserAccess" when :
    "parsing a valid response" should :
      "return a UserAccess" in new Setup:
        stubFor(
          get(urlEqualTo(s"/v2/organisations/users/$username/access"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("valid-user-access.json")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res = userManagementConnector.getUserAccess(username).futureValue

        res shouldBe Some(UserAccess(vpn = true, jira = true, confluence = true, devTools = true, googleApps = true))

    "it receives a 404 status code response" should :
      "recover and return None" in new Setup:
        stubFor(
          get(urlEqualTo(s"/v2/organisations/users/$username/access"))
            .willReturn(
              aResponse()
                .withStatus(404)
            )
        )

        val res = userManagementConnector.getUserAccess(username).futureValue
        res shouldBe None

    "it receives a non 200 status code response" should :
      "recover and return None" in new Setup:
        stubFor(
          get(urlEqualTo(s"/v2/organisations/users/$username/access"))
            .willReturn(
              aResponse()
                .withStatus(500)
            )
        )

        val res = userManagementConnector.getAllTeams().failed.futureValue
        res shouldBe a[UpstreamErrorResponse]

  "getAllTeams" when:
    "parsing a valid response" should:
      "return a sequence of Teams" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/teams"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("valid-teams.json")
            )
        )

        val res = userManagementConnector.getAllTeams().futureValue

        res should contain theSameElementsAs Seq(
          Team(members = Seq(), teamName = "PlatOps",    description = Some("A great team"), documentation = Some("Confluence"), slack = Some("https://slackchannel.com"), slackNotification = Some("https://slackchannel2.com")),
          Team(members = Seq(), teamName = "Other Team", description = None,                 documentation = None,               slack = None, slackNotification = None)
        )

    "parsing an invalid JSON response" should:
      "throw a JSValidationException" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/teams"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("invalid-teams.json")
            )
        )

        val res = userManagementConnector.getAllTeams().failed.futureValue
        res shouldBe a [JsValidationException]

    "it receives a non 200 status code response" should:
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/teams"))
            .willReturn(
              aResponse()
                .withStatus(500)
            )
        )

        val res = userManagementConnector.getAllTeams().failed.futureValue
        res shouldBe a [UpstreamErrorResponse]

  "getMembersForTeam" when:
    "parsing a valid response" should:
      "return a Team with non human users flagged" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("valid-members-of-team.json")
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").futureValue

        res shouldBe Some(Team(
          members           = Seq(
                                Member(username = "joe.bloggs"     , displayName = Some("Joe Bloggs"), role = "team_admin", isNonHuman = false),
                                Member(username = "jane.doe"       , displayName = Some("Jane Doe")  , role = "user"      , isNonHuman = false),
                                Member(username = "service-account", displayName = Some("service")   , role = "user"      , isNonHuman = true ),
                                Member(username = "PLaToPs"        , displayName = Some("platops")   , role = "user"      , isNonHuman = true ),
                                Member(username = "BUILD"          , displayName = Some("build")     , role = "user"      , isNonHuman = true ),
                                Member(username = "DePlOy"         , displayName = Some("deploy")    , role = "user"      , isNonHuman = true ),
                                Member(username = "ddcops_"        , displayName = Some("ddcops")    , role = "user"      , isNonHuman = true ),
                                Member(username = "Deskpro"        , displayName = Some("deskpro")   , role = "user"      , isNonHuman = true ),
                                Member(username = "platSEC"        , displayName = Some("platSEC")   , role = "user"      , isNonHuman = true ),
                              ),
          teamName          = "PlatOps",
          description       = None,
          documentation     = None,
          slack             = Some("https://slack.com"),
          slackNotification = None
        ))

    "parsing an invalid JSON response" should:
      "throw a JSValidationException" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("invalid-members-of-team.json")
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").failed.futureValue
        res shouldBe a [JsValidationException]

    "it receives a 404 status code response" should:
      "recover with a None" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(404)
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").futureValue
        res shouldBe None

    "it receives a 422 status code response" should:
      "recover with a None" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(422)
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").futureValue
        res shouldBe None

    "it receives any other non 200 status code response" should:
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          get(urlEqualTo("/v2/organisations/teams/PlatOps/members"))
            .willReturn(
              aResponse()
                .withStatus(500)
            )
        )

        val res = userManagementConnector.getTeamWithMembers("PlatOps").failed.futureValue
        res shouldBe a [UpstreamErrorResponse]

  "requestNewVpnCert" when :
    "parsing a valid response" should :
      "return unit" in new Setup:
        stubFor(
          post(urlEqualTo("/v2/vpn/create_certificate_request/tom.test"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody("""{"ticket_number": "ACRS-1234"}""")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res: JsValue =
          userManagementConnector.requestNewVpnCert("tom.test").futureValue

        res shouldBe Json.parse("""{"ticket_number": "ACRS-1234"}""")

    "it receives a non 2xx status code response" should :
      "return an UpstreamErrorResponse" in new Setup:
        stubFor(
          post(urlEqualTo("/v2/vpn/create_certificate_request/tom.test"))
            .willReturn(
              aResponse()
                .withStatus(500)
                .withBody("""{"message": "internal server error"}""")
            )
        )

        stubFor(
          get(urlEqualTo("/internal-auth/ump/token"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(JsString("token").toString)
            )
        )

        val res: Throwable =
          userManagementConnector.requestNewVpnCert("tom.test").failed.futureValue

        res shouldBe a [UpstreamErrorResponse]
end UmpConnectorSpec

trait Setup:
  stubFor(
    post(urlEqualTo("/v1/login"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""{"Token":"token","uid": "uid"}""")
      )
  )

  val createUserRequest: CreateUserRequest =
    CreateUserRequest(
      contactComments = "comments",
      contactEmail = "email@address",
      familyName = "doe",
      givenName = "john",
      organisation = "SomeOrg",
      team = "SomeTeam",
      userDisplayName = "John Doe",
      access = Access(
        ldap = true,
        vpn = true,
        jira = true,
        confluence = true,
        environments = true,
        googleApps = true,
        bitwarden = true
      ),
      isReturningUser = false,
      isTransitoryUser = false,
      isExistingLDAPUser = false
    )

  val editUserDetailsRequest: EditUserDetailsRequest =
    EditUserDetailsRequest(
      username = "joe.bloggs",
      attribute = UserAttribute.DisplayName,
      value = "joseph.bloggs"
    )

  val username = "john.doe"

  val editUserAccessRequest: EditUserAccessRequest =
    EditUserAccessRequest(
      username = username,
      organisation = "SomeOrg",
      access = EditAccess(
        vpn = true,
        jira = true,
        confluence = true,
        environments = true,
        googleApps = true,
        bitwarden = true
      ),
      isExistingLDAPUser = true
    )

  val resetLdapPassword: ResetLdapPassword =
    ResetLdapPassword(
      username = username,
      email = "test@test.com"
    )

package uk.gov.hmrc.usermanagement.connectors

import akka.Done
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, stubFor, urlEqualTo}
import org.scalatest.EitherValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.usermanagement.config.{UserManagementAuthConfig, UserManagementPortalConfig}
import uk.gov.hmrc.usermanagement.connectors.UserManagementConnector.UMPError
import uk.gov.hmrc.usermanagement.model.{Team, User}
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

          val res = userManagementConnector.getAllUsers.futureValue.right.value

          res should contain theSameElementsAs Seq(
              User(displayName = Some("Joe Bloggs"), familyName = "Bloggs", givenName = "Joe", organisation = Some("MDTP"), primaryEmail = "joe.bloggs@gmail.com", username = "joe.bloggs", github = Some("https://github.com/hmrc"), phoneNumber = Some("12345678912"), role = None),
              User(displayName = Some("Jane Doe"), familyName = "Doe", givenName = "Jane", organisation = None, primaryEmail = "jane.doe@gmail.com", username = "jane.doe", github = None, phoneNumber = None, role = None)
            )
        }
      }

      "parsing an invalid JSON response" should {
        "return a UMP connection error, containing a JSonValidationError" in {
          stubFor(
            WireMock.get(urlEqualTo("/v2/organisations/users"))
              .willReturn(
                aResponse()
                  .withStatus(200)
                  .withBodyFile("invalid-users.json")
              )
          )

          val res = userManagementConnector.getAllUsers.futureValue.left.value
          res.isInstanceOf[UMPError.ConnectionError]
          res.errorMsg should include("JsonValidationError")
        }
      }

      "it receives a non 200 status code response" should {
        "return a UMP HTTP error, describing the status code" in {
          stubFor(
            WireMock.get(urlEqualTo("/v2/organisations/users"))
              .willReturn(
                aResponse()
                  .withStatus(500)
              )
          )

          val res = userManagementConnector.getAllUsers.futureValue.left.value
          res shouldBe UMPError.HTTPError(500)
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

        val res = userManagementConnector.getAllTeams.futureValue.right.value

        res should contain theSameElementsAs Seq(
           Team(members = Seq(), team = "PlatOps", description = Some("A great team"), documentation = Some("Confluence"), slack = Some("https://slackchannel.com"), slackNotification = Some("https://slackchannel2.com")),
          Team(members = Seq(), team = "Other Team", description = None, documentation = None, slack = None, slackNotification = None)
        )
      }
    }
  }

  "parsing an invalid JSON response" should {
    "return a UMP connection error, containing a JSonValidationError" in {
      stubFor(
        WireMock.get(urlEqualTo("/v2/organisations/teams"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBodyFile("invalid-teams.json")
          )
      )

      val res = userManagementConnector.getAllTeams.futureValue.left.value
      res.isInstanceOf[UMPError.ConnectionError]
      res.errorMsg should include("JsonValidationError")
    }
  }

  "it receives a non 200 status code response" should {
    "return a UMP HTTP error, describing the status code" in {
      stubFor(
        WireMock.get(urlEqualTo("/v2/organisations/teams"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      val res = userManagementConnector.getAllTeams.futureValue.left.value
      res shouldBe UMPError.HTTPError(500)
    }
  }

}

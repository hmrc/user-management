package uk.gov.hmrc.usermanagement.connectors

import play.api.Logging
import play.api.cache.AsyncCacheApi
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{Json, OFormat, __}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.usermanagement.config.{UserManagementAuthConfig, UserManagementPortalConfig}
import uk.gov.hmrc.usermanagement.model.{Team, User}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
 class UserManagementConnector @Inject()(
  umpConfig     : UserManagementPortalConfig,
  httpClientV2  : HttpClientV2,
  authConfig    : UserManagementAuthConfig,
  tokenCache    : AsyncCacheApi
)(implicit ec: ExecutionContext) extends Logging {

  import umpConfig._
  import UserManagementConnector._
  import authConfig._
  import uk.gov.hmrc.http.HttpReads.Implicits._


  def retrieveToken(): Future[UmpToken] =
    if (authEnabled)
      tokenCache.getOrElseUpdate[UmpToken]("token", tokenTTL)(login())
    else
      Future.successful(NoTokenRequired)

  def login(): Future[UmpAuthToken] = {
    implicit val lrf: OFormat[UmpLoginRequest] = UmpLoginRequest.format
    implicit val atf: OFormat[UmpAuthToken]    = UmpAuthToken.format
    implicit val hc: HeaderCarrier             = HeaderCarrier()
    for {
      token <- httpClientV2.post(url"$userManagementLoginUrl")
        .withBody(Json.toJson(UmpLoginRequest(username, password)))
        .execute[UmpAuthToken]
      _      = logger.info("logged into UMP")
    } yield token
  }

  def getAllUsers(implicit hc: HeaderCarrier): Future[Either[UMPError, Seq[User]]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/users"
    implicit val uf = User.format

    for {
      token <- retrieveToken()
      resp  <- httpClientV2
        .get(url)
        .setHeader(token.asHeaders():_*)
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case 200 =>
              (response.json \\ "users").headOption
                .map(_.as[Seq[User]])
                .fold[Either[UMPError, Seq[User]]](ifEmpty = Left(UMPError.ConnectionError(s"Could not parse response from $url")))(Right.apply)
            case httpCode => Left(UMPError.HTTPError(httpCode))
          }
        }
        .recover {
          case ex =>
            logger.error(s"An error occurred when connecting to $url", ex)
            Left(UMPError.ConnectionError(s"Could not connect to $url: ${ex.getMessage}"))
        }
    } yield resp
  }.recover {
    case ex =>
      logger.error(s"Failed to login to UMP", ex)
      Left(UMPError.ConnectionError(s"Failed to login to UMP: ${ex.getMessage}"))
  }

  def getAllTeams(implicit hc: HeaderCarrier): Future[Either[UMPError, Seq[Team]]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/teams"
    implicit val uf = Team.format

    for {
      token <- retrieveToken()
      resp  <- httpClientV2
        .get(url)
        .setHeader(token.asHeaders():_*)
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case 200 =>
              (response.json \\ "teams").headOption
                .map(_.as[Seq[Team]])
                .fold[Either[UMPError, Seq[Team]]](ifEmpty = Left(UMPError.ConnectionError(s"Could not parse response from $url")))(Right.apply)
            case httpCode => Left(UMPError.HTTPError(httpCode))
          }
        }
        .recover {
          case ex =>
            logger.error(s"An error occurred when connecting to $url", ex)
            Left(UMPError.ConnectionError(s"Could not connect to $url: ${ex.getMessage}"))
        }
    } yield resp
  }.recover {
    case ex =>
      logger.error(s"Failed to login to UMP", ex)
      Left(UMPError.ConnectionError(s"Failed to login to UMP: ${ex.getMessage}"))
  }




}

object UserManagementConnector {

  sealed trait UmpToken {
    def asHeaders(): Seq[(String, String)]
  }

  case class UmpAuthToken(token: String, uid: String) extends UmpToken {
    def asHeaders(): Seq[(String, String)] = {
      Seq( "Token" -> token, "requester" -> uid)
    }
  }

  case object NoTokenRequired extends UmpToken {
    override def asHeaders(): Seq[(String, String)] = Seq.empty
  }

  object UmpAuthToken {
    val format: OFormat[UmpAuthToken] = (
      (__ \ "Token").format[String]
        ~ (__ \ "uid").format[String]
      )(UmpAuthToken.apply, unlift(UmpAuthToken.unapply))
  }

  case class UmpLoginRequest(username: String, password:String)

  object UmpLoginRequest {
    val format: OFormat[UmpLoginRequest] =
      ( (__ \ "username").format[String]
        ~ (__ \ "password").format[String]
        )(UmpLoginRequest.apply, unlift(UmpLoginRequest.unapply))
  }

  sealed trait UMPError {
    def errorMsg: String = ???
  }

  object UMPError {
    case object UnknownTeam                    extends UMPError {
      override def errorMsg: String = "Unknown team provided to the UMP API"
    }
    case class  HTTPError(code: Int)           extends UMPError {
      override def errorMsg: String = s"Received a $code status code from the UMP API"
    }
    case class  ConnectionError(error: String) extends UMPError {
      override def errorMsg: String = error
    }
  }
}

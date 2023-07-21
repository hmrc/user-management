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

import play.api.{Configuration, Logging}
import play.api.cache.AsyncCacheApi
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{Json, OFormat, Reads, __}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.usermanagement.model.{Team, User}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
 class UserManagementConnector @Inject()(
  config        : Configuration,
  httpClientV2  : HttpClientV2,
  tokenCache    : AsyncCacheApi
)(implicit ec: ExecutionContext) extends Logging {

  val userManagementBaseUrl : String   = config.get[String]("ump.baseUrl")
  val userManagementLoginUrl: String   = config.get[String]("ump.loginUrl")
  val tokenTTL              : Duration = config.get[Duration]("ump.auth.tokenTTL")
  val username              : String   = config.get[String]("ump.auth.username")
  val password              : String   = config.get[String]("ump.auth.password")

  import UserManagementConnector._

  def retrieveToken(): Future[UmpToken] =
      tokenCache.getOrElseUpdate[UmpToken]("token", tokenTTL)(login())

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

  def getAllUsers()(implicit hc: HeaderCarrier): Future[Seq[User]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/users"

    for {
      token <- retrieveToken()
      resp  <- httpClientV2
        .get(url)
        .setHeader(token.asHeaders():_*)
        .execute[UmpUsers]
        .map(_.users)
    } yield resp
      .filterNot { user =>
        nonHumanIdentifiers.exists(user.username.toLowerCase.contains(_))
      }
  }

  def getAllTeams()(implicit hc: HeaderCarrier): Future[Seq[Team]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/teams"

    for {
      token <- retrieveToken()
      resp  <- httpClientV2
        .get(url)
        .setHeader(token.asHeaders():_*)
        .execute[UmpTeams]
        .map(_.teams)
    } yield resp
  }

  def getTeamWithMembers(teamName: String)(implicit hc: HeaderCarrier): Future[Option[Team]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/teams/$teamName/members"
    implicit val uf = Team.umpReads

    for {
      token <- retrieveToken()
      resp <- httpClientV2
        .get(url)
        .setHeader(token.asHeaders():_*)
        .execute[Option[Team]]
        .recover {
          case UpstreamErrorResponse.WithStatusCode(422) =>
            logger.warn(s"Received a 422 response when getting membersForTeam for teamname: $teamName. " +
              s"This is a known issue that can occur when a team has been created in UMP with invalid characters in its name.")
            None
          case UpstreamErrorResponse.WithStatusCode(404) =>
            logger.warn(s"Received a 404 response when getting membersForTeam for teamname: $teamName. " +
              s"This indicates the team does not exist within UMP.")
            None
        }
    } yield resp.map { team =>
      team.copy(members = team.members.filterNot { member =>
        nonHumanIdentifiers.exists(member.username.toLowerCase.contains(_))
      })
    }
  }
}

object UserManagementConnector {

  val nonHumanIdentifiers: Seq[String] = Seq("service", "platops", "build", "deploy", "deskpro", "ddcops", "platsec")

  implicit val uf = User.format
  implicit val tf = Team.umpReads

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

  case class UmpUsers(
    users: Seq[User]
  )

  object UmpUsers {
    implicit val reads: Reads[UmpUsers] = Json.reads[UmpUsers]
  }

  case class UmpTeams(
    teams: Seq[Team]
  )

  object UmpTeams {
    implicit val reads: Reads[UmpTeams] = Json.reads[UmpTeams]
  }
}

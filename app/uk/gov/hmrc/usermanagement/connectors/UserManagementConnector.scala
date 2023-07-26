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
import play.api.libs.json.{Json, OWrites, Reads, __}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.usermanagement.model.{Member, Team, User}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
 class UserManagementConnector @Inject()(
  config        : Configuration,
  httpClientV2  : HttpClientV2,
  tokenCache    : AsyncCacheApi
)(implicit ec: ExecutionContext) extends Logging {

  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val userManagementBaseUrl : String   = config.get[String]("ump.baseUrl")
  private val userManagementLoginUrl: String   = config.get[String]("ump.loginUrl")
  private val tokenTTL              : Duration = config.get[Duration]("ump.auth.tokenTTL")
  private val username              : String   = config.get[String]("ump.auth.username")
  private val password              : String   = config.get[String]("ump.auth.password")

  import UserManagementConnector._

  private def getToken(): Future[UmpAuthToken] =
      tokenCache.getOrElseUpdate[UmpAuthToken]("token", tokenTTL)(retrieveToken())

  private def retrieveToken(): Future[UmpAuthToken] = {
    implicit val lrw: OWrites[UmpLoginRequest] = UmpLoginRequest.writes
    implicit val atr: Reads[UmpAuthToken]      = UmpAuthToken.reads
    implicit val hc: HeaderCarrier             = HeaderCarrier()
    for {
      token <- httpClientV2.post(url"$userManagementLoginUrl")
        .withBody(Json.toJson(UmpLoginRequest(username, password)))
        .execute[UmpAuthToken]
      _      = logger.info("logged into UMP")
    } yield token
  }

  def getAllUsers()(implicit hc: HeaderCarrier): Future[Seq[User]] = {
    implicit val ur = {
      implicit val uf = User.format
      Reads.at[Seq[User]](__ \ "users")
    }

    for {
      token <- getToken()
      resp  <- httpClientV2
        .get(url"$userManagementBaseUrl/v2/organisations/users")
        .setHeader(token.asHeaders():_*)
        .execute[Seq[User]]
    } yield resp
      .filterNot { user =>
        nonHumanIdentifiers.exists(user.username.toLowerCase.contains(_))
      }
  }

  def getAllTeams()(implicit hc: HeaderCarrier): Future[Seq[Team]] = {
    implicit val tr = {
      implicit val tr = umpTeamReads
      Reads.at[Seq[Team]](__ \ "teams")
    }

    for {
      token <- getToken()
      resp  <- httpClientV2
        .get(url"$userManagementBaseUrl/v2/organisations/teams")
        .setHeader(token.asHeaders():_*)
        .execute[Seq[Team]]
    } yield resp
  }

  def getTeamWithMembers(teamName: String)(implicit hc: HeaderCarrier): Future[Option[Team]] = {
    implicit val tr = umpTeamReads
    for {
      token <- getToken()
      resp  <- httpClientV2
        .get(url"$userManagementBaseUrl/v2/organisations/teams/$teamName/members")
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

  case class UmpAuthToken(token: String, uid: String) {
    def asHeaders(): Seq[(String, String)] = {
      Seq( "Token" -> token, "requester" -> uid)
    }
  }

  object UmpAuthToken {
    val reads: Reads[UmpAuthToken] = (
      (__ \ "Token").read[String]
        ~ (__ \ "uid").read[String]
      )(UmpAuthToken.apply _)
  }

  case class UmpLoginRequest(username: String, password:String)

  object UmpLoginRequest {
    val writes: OWrites[UmpLoginRequest] =
      ( (__ \ "username").write[String]
        ~ (__ \ "password").write[String]
        )(unlift(UmpLoginRequest.unapply))
  }

  val umpTeamReads: Reads[Team] = {
    implicit val tmf = Member.format
    ((__ \ "members"            ).read[Seq[Member]]
      ~ (__ \"team"             ).read[String]
      ~ (__ \"description"      ).readNullable[String]
      ~ (__ \"documentation"    ).readNullable[String]
      ~ (__ \"slack"            ).readNullable[String]
      ~ (__ \"slackNotification").readNullable[String]
      )(Team.apply _)
  }

}

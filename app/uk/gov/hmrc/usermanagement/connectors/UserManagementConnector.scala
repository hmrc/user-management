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

import play.api.Logging
import play.api.cache.AsyncCacheApi
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{Json, OFormat, Reads, __}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.usermanagement.config.{UserManagementAuthConfig, UserManagementPortalConfig}
import uk.gov.hmrc.usermanagement.model.{Team, TeamMember, User}

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

  def getAllUsers()(implicit hc: HeaderCarrier): Future[Seq[User]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/users"
    implicit val uf = User.format

    for {
      token <- retrieveToken()
      resp  <- httpClientV2
        .get(url)
        .setHeader(token.asHeaders():_*)
        .execute[UmpUsers]
        .map(_.users)
    } yield resp
  }

  def getAllTeams()(implicit hc: HeaderCarrier): Future[Seq[Team]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/teams"
    implicit val uf = Team.umpReads

    for {
      token <- retrieveToken()
      resp  <- httpClientV2
        .get(url)
        .setHeader(token.asHeaders():_*)
        .execute[UmpTeams]
        .map(_.teams)
    } yield resp
  }

  def getMembersForTeam(teamName: String)(implicit hc: HeaderCarrier): Future[Option[Seq[TeamMember]]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/teams/$teamName/members"
    implicit val uf = TeamMember.reads

    for {
      token <- retrieveToken()
      resp <- httpClientV2
        .get(url)
        .setHeader(token.asHeaders():_*)
        .execute[Option[UmpTeamMembers]]
        .map(_.map(_.members))
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
    } yield resp
  }
}

object UserManagementConnector {

  implicit val uf = User.format
  implicit val tf = Team.umpReads
  implicit val tmr = TeamMember.reads

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

  case class UmpTeamMembers(
    members: Seq[TeamMember]
  )

  object UmpTeamMembers {
      implicit val reads: Reads[UmpTeamMembers] = Json.reads[UmpTeamMembers]
  }
}

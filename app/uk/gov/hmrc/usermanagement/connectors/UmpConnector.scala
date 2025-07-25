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
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.usermanagement.model.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

@Singleton
class UmpConnector @Inject()(
  config        : Configuration,
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig,
  tokenCache    : AsyncCacheApi
)(using
  ExecutionContext
) extends Logging:

  import uk.gov.hmrc.http.HttpReads.Implicits.*

  private val userManagementBaseUrl : String   = config.get[String]("ump.baseUrl")
  private val userManagementLoginUrl: String   = config.get[String]("ump.loginUrl")
  private val tokenTTL              : Duration = config.get[Duration]("ump.auth.tokenTTL")
  private val username              : String   = config.get[String]("ump.auth.username")
  private val password              : String   = config.get[String]("ump.auth.password")

  import UmpConnector.*

  private def getUsersUmpToken()(using hc: HeaderCarrier): Future[UsersUmpAuthToken] =
    given Reads[UsersUmpAuthToken] = UsersUmpAuthToken.reads
    val internalAuthBaseUrl   = servicesConfig.getConfString(
        "internal-auth.url",
        servicesConfig.baseUrl("internal-auth")
    )
    httpClientV2
      .get(url"$internalAuthBaseUrl/internal-auth/ump/token")
      .execute[UsersUmpAuthToken]

  private def getUserManagementUmpToken(): Future[UmpAuthToken] =
    tokenCache.getOrElseUpdate[UmpAuthToken]("token", tokenTTL)(retrieveToken())

  private def retrieveToken(): Future[UmpAuthToken] =
    given OWrites[UmpLoginRequest] = UmpLoginRequest.writes
    given Reads[UmpAuthToken]      = UmpAuthToken.reads
    given HeaderCarrier            = HeaderCarrier()

    for
      token <- httpClientV2.post(url"$userManagementLoginUrl")
                 .withBody(Json.toJson(UmpLoginRequest(username, password)))
                 .execute[UmpAuthToken]
      _     =  logger.info("logged into UMP")
    yield token

  def getAllUsers()(using HeaderCarrier): Future[Seq[User]] =
    given Reads[Seq[User]] = readsAtUsers
    for
      token <- getUserManagementUmpToken()
      resp  <- httpClientV2
                 .get(url"$userManagementBaseUrl/v2/organisations/users?includeDeleted=true")
                 .setHeader(token.asHeaders():_*)
                 .execute[Seq[User]]
    yield
      resp.map:
        case user if nonHumanIdentifiers.exists(user.username.toLowerCase.contains(_)) => user.copy(isNonHuman = true)
        case user => user

  def createUser(createUserRequest: CreateUserRequest)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .post(url"$userManagementBaseUrl/v2/user_requests/users/none")
          .setHeader(token.asHeaders():_*)
          .withBody(Json.toJson(createUserRequest)(CreateUserRequest.writes))
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e)  => Future.failed(e)

  def editUserDetails(editUserDetailsRequest: EditUserDetailsRequest)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .put(url"$userManagementBaseUrl/v2/organisations/users/${editUserDetailsRequest.username}/${editUserDetailsRequest.attribute.name}")
          .setHeader(token.asHeaders():_*)
          .withBody(Json.toJson(editUserDetailsRequest)(EditUserDetailsRequest.writes))
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e) => Future.failed(e)

  def createTeam(createTeamRequest: CreateTeamRequest)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .post(url"$userManagementBaseUrl/v2/organisations/teams")
          .setHeader(token.asHeaders(): _*)
          .withBody(Json.toJson(createTeamRequest)(CreateTeamRequest.formats))
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e) => Future.failed(e)

  def editTeamDetails(editTeamDetails: EditTeamDetails)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .patch(url"$userManagementBaseUrl/v2/organisations/teams/${editTeamDetails.team}")
          .setHeader(token.asHeaders(): _*)
          .withBody(Json.toJson(editTeamDetails)(EditTeamDetails.writes))
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e) => Future.failed(e)

  def deleteTeam(teamName: String)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .delete(url"$userManagementBaseUrl/v2/organisations/teams/$teamName")
          .setHeader(token.asHeaders(): _*)
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e) => Future.failed(e)

  def resetUserGooglePassword(resetGooglePassword: ResetGooglePassword)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .put(url"$userManagementBaseUrl/v2/googleapps/users/${resetGooglePassword.username}/password")
          .setHeader(token.asHeaders():_*)
          .withBody(Json.toJson(resetGooglePassword)(ResetGooglePassword.writes))
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e) => Future.failed(e)

  def editUserAccess(editUserAccessRequest: EditUserAccessRequest)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .post(url"$userManagementBaseUrl/v2/user_requests/users/${editUserAccessRequest.username}")
          .setHeader(token.asHeaders():_*)
          .withBody(Json.toJson(editUserAccessRequest)(EditUserAccessRequest.writes))
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e) => Future.failed(e)

  def getUserAccess(username: String)(using HeaderCarrier): Future[Option[UserAccess]] =
    given Reads[UserAccess] = UserAccess.reads
    getUserManagementUmpToken()
      .flatMap: token =>
        httpClientV2
          .get(url"$userManagementBaseUrl/v2/organisations/users/$username/access")
          .setHeader(token.asHeaders():_*)
          .execute[Option[UserAccess]]
          .recover:
            case UpstreamErrorResponse.WithStatusCode(404) =>
              logger.warn(s"Received a 404 response when getting access for user: $username. " +
                s"This indicates the user does not exist within UMP.")
              None

  def getUserRoles(username: String)(using HeaderCarrier): Future[UserRoles] =
    given Reads[UserRoles] = UserRoles.reads
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .get(url"$userManagementBaseUrl/v2/roles/users/$username")
          .setHeader(token.asHeaders(): _*)
          .execute[UserRoles]

  def editUserRoles(username: String, userRoles: UserRoles)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .post(url"$userManagementBaseUrl/v2/roles/users/$username")
          .setHeader(token.asHeaders(): _*)
          .withBody(Json.toJson(userRoles)(UserRoles.writes))
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e)  => Future.failed(e)

  def resetUserLdapPassword(resetLdapPassword: ResetLdapPassword)(using HeaderCarrier): Future[JsValue] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .put(url"$userManagementBaseUrl/v2/organisations/users/${resetLdapPassword.username}/password")
          .setHeader(token.asHeaders():_*)
          .withBody(Json.toJson(resetLdapPassword)(ResetLdapPassword.writes))
          .execute[Either[UpstreamErrorResponse, JsValue]]
          .flatMap:
            case Right(json) => Future.successful(json)
            case Left(e)     => Future.failed(e)

  def getAllTeams()(using HeaderCarrier): Future[Seq[Team]] =
    given Reads[Seq[Team]] = readsAtTeams
    for
      token <- getUserManagementUmpToken()
      resp  <- httpClientV2
                 .get(url"$userManagementBaseUrl/v2/organisations/teams")
                 .setHeader(token.asHeaders():_*)
                 .execute[Seq[Team]]
    yield resp

  def getTeamWithMembers(teamName: String)(using HeaderCarrier): Future[Option[Team]] =
    given Reads[Team] = umpTeamReads
    for
      token <- getUserManagementUmpToken()
      resp  <- httpClientV2
                 .get(url"$userManagementBaseUrl/v2/organisations/teams/$teamName/members")
                 .setHeader(token.asHeaders():_*)
                 .execute[Option[Team]]
                 .recover:
                   case UpstreamErrorResponse.WithStatusCode(422) =>
                     logger.warn(s"Received a 422 response when getting membersForTeam for teamname: $teamName. " +
                       s"This is a known issue that can occur when a team has been created in UMP with invalid characters in its name.")
                     None
                   case UpstreamErrorResponse.WithStatusCode(404) =>
                     logger.warn(s"Received a 404 response when getting membersForTeam for teamname: $teamName. " +
                       s"This indicates the team does not exist within UMP.")
                     None
    yield resp.map: team =>
      team.copy(members = team.members.map:
        case member if nonHumanIdentifiers.exists(member.username.toLowerCase.contains(_)) => member.copy(isNonHuman = true)
        case member => member
      )

  def manageVpnAccess(username: String, enableVpn: Boolean)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .post(url"$userManagementBaseUrl/v2/vpn/users/$username")
          .setHeader(token.asHeaders(): _*)
          .withBody(Json.parse(s"""{"isVPNUser":$enableVpn}"""))
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(json) => Future.unit
            case Left(e)     => Future.failed(e)


  def manageDevToolsAccess(username: String, enableDevTools: Boolean)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .post(url"$userManagementBaseUrl/v2/environments/users/$username")
          .setHeader(token.asHeaders(): _*)
          .withBody(devToolsPayload(enableDevTools))
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(json) => Future.unit
            case Left(e) => Future.failed(e)

  def requestNewVpnCert(username: String)(using HeaderCarrier): Future[JsValue] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .post(url"$userManagementBaseUrl/v2/vpn/create_certificate_request/$username")
          .setHeader(token.asHeaders():_*)
          .execute[Either[UpstreamErrorResponse, JsValue]]
          .flatMap:
            case Right(json) => Future.successful(json)
            case Left(e)     => Future.failed(e)

  def addUserToGithubTeam(username: String, team: String)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .put(url"$userManagementBaseUrl/v2/github/teams/$team/members/$username")
          .setHeader(token.asHeaders():_*)
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e)  => Future.failed(e)

  def addUserToTeam(team: String, username: String)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .put(url"$userManagementBaseUrl/v2/organisations/teams/$team/members/$username")
          .setHeader(token.asHeaders():_*)
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e)  => Future.failed(e)

  def removeUserFromTeam(team: String, username: String)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .delete(url"$userManagementBaseUrl/v2/organisations/teams/$team/members/$username")
          .setHeader(token.asHeaders():_*)
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e)  => Future.failed(e)
              
  def offboardUsers(offboardUsersRequest: OffBoardUsersRequest)(using HeaderCarrier): Future[Unit] =
    getUsersUmpToken()
      .flatMap: token =>
        httpClientV2
          .post(url"$userManagementBaseUrl/v2/tasks")
          .setHeader(token.asHeaders():_*)
          .withBody(Json.toJson(offboardUsersRequest)(OffBoardUsersRequest.writes))
          .execute[Either[UpstreamErrorResponse, Unit]]
          .flatMap:
            case Right(_) => Future.unit
            case Left(e)  => Future.failed(e)
            
end UmpConnector

object UmpConnector:
  val nonHumanIdentifiers: Seq[String] =
    Seq("service", "platops", "build", "deploy", "deskpro", "ddcops", "platsec")

  case class UmpAuthToken(token: String, uid: String):
    def asHeaders(): Seq[(String, String)] =
      Seq("Token" -> token, "requester" -> uid)

  object UmpAuthToken:
    val reads: Reads[UmpAuthToken] =
      ( (__ \ "Token").read[String]
      ~ (__ \ "uid"  ).read[String]
      )(UmpAuthToken.apply _)

  case class UsersUmpAuthToken(token: String):
    def asHeaders(): Seq[(String, String)] =
      Seq("Token" -> token)

  object UsersUmpAuthToken:
    val reads: Reads[UsersUmpAuthToken] =
      __.read[String].map(UsersUmpAuthToken.apply)

  case class UmpLoginRequest(
    username: String,
    password: String
  )

  object UmpLoginRequest:
    val writes: OWrites[UmpLoginRequest] =
      ( (__ \ "username").write[String]
      ~ (__ \ "password").write[String]
      )(pt => Tuple.fromProductTyped(pt))

  val umpUserReads: Reads[User] =
    ( ( __ \ "displayName"  ).readNullable[String]
    ~ ( __ \ "familyName"   ).read[String]
    ~ ( __ \ "givenName"    ).readNullable[String]
    ~ ( __ \ "organisation" ).readNullable[String]
    ~ ( __ \ "primaryEmail" ).read[String]
    ~ Reads.pure(None) // slackId will be copied in later
    ~ ( __ \ "username"     ).read[String]
    ~ ( __ \ "github"       ).readNullable[String].map(_.map(_.split('/').last))
    ~ ( __ \ "phoneNumber"  ).readNullable[String]
    ~ ( __ \ "role"         ).readWithDefault[String]("user")
    ~ ( __ \ "teamNames"    ).readWithDefault[Seq[String]](Seq.empty[String])
    ~ ( __ \ "isDeleted"    ).readWithDefault[Boolean](false)
    ~ ( __ \ "isNonHuman"   ).readWithDefault[Boolean](false)
    )(User.apply _)

  val readsAtUsers: Reads[Seq[User]] =
    given Reads[User] = umpUserReads
    Reads.at[Seq[User]](__ \ "users")

  val umpTeamReads: Reads[Team] =
    given Format[Member] = Member.format
    ( (__ \ "members"          ).read[Seq[Member]]
    ~ (__ \ "team"             ).read[String]
    ~ (__ \ "description"      ).readNullable[String]
    ~ (__ \ "documentation"    ).readNullable[String]
    ~ (__ \ "slack"            ).readNullable[String]
    ~ (__ \ "slackNotification").readNullable[String]
    )(Team.apply _)

  val readsAtTeams: Reads[Seq[Team]] =
    given Reads[Team] = umpTeamReads
    Reads.at[Seq[Team]](__ \ "teams")

  def devToolsPayload(enableDevTools: Boolean): JsObject =
    Json.obj(
      "prod"         -> (if enableDevTools then Json.arr("dev-tools") else Json.arr()),
      "qa-left"      -> Json.arr(),
      "qa-right"     -> Json.arr(),
      "staging-left" -> Json.arr(),
      "dev"          -> Json.arr(),
      "build"        -> Json.arr()
    )

end UmpConnector

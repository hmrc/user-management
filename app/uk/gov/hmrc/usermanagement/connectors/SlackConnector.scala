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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import play.api.{Configuration, Logging}
import play.api.libs.functional.syntax.*
import play.api.libs.json.{JsValue, Json, Reads, __}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.usermanagement.model.SlackUser

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlackConnector @Inject()(
  httpClientV2 : HttpClientV2,
  configuration: Configuration
)(using
  ExecutionContext
) extends Logging:
  private lazy val apiUrl: String =
    configuration.get[String]("slack.apiUrl")

  private lazy val token: String =
    configuration.get[String]("slack.token")

  private lazy val limit: Int =
    configuration.get[Int]("slack.limit")

  private lazy val requestThrottle: FiniteDuration =
    configuration.get[FiniteDuration]("slack.requestThrottle")

  private def getSlackUsersPage(cursor: String)(using HeaderCarrier): Future[SlackUserListPage] =
    given Reads[SlackUserListPage] = SlackUserListPage.reads
    httpClientV2
      .get(url"$apiUrl/users.list?limit=$limit&cursor=$cursor")
      .setHeader("Authorization" -> s"Bearer $token")
      .withProxy
      .execute[SlackUserListPage]

  def getAllSlackUsers()(using Materializer, HeaderCarrier): Future[Seq[SlackUser]] =
    Source.unfoldAsync(Option(""): Option[String]):
      case None         => Future.successful(None)
      case Some(cursor) =>
        getSlackUsersPage(cursor).map: result =>
          if   result.nextCursor.isEmpty()
          then Some((None, result))
          else Some((Some(result.nextCursor), result))
    .throttle(1, requestThrottle)
    .runFold(Seq.empty[SlackUser]): (acc, page) =>
      acc ++ page.members

  def lookupUserByEmail(email: String)(using HeaderCarrier): Future[Option[SlackUser]] =
    given Reads[SlackUserResponse] = SlackUserResponse.reads
    httpClientV2
      .get(url"$apiUrl/users.lookupByEmail?email=$email")
      .setHeader("Authorization" -> s"Bearer $token")
      .withProxy
      .execute[Either[UpstreamErrorResponse, SlackUserResponse]]
      .flatMap:
        case Right(r) =>
          Future.successful(if r.ok then r.user else None)
        case Left(e)  =>
          logger.error(s"Slack API returned ${e.statusCode} for users.lookupByEmail for $email: ${e.getMessage}", e)
          Future.failed(e)

  private def getSlackChannelsPage(cursor: String)(using HeaderCarrier): Future[SlackChannelListPage] =
    given Reads[SlackChannelListPage] = SlackChannelListPage.reads
    httpClientV2
      .get(url"$apiUrl/conversations.list?limit=$limit&cursor=$cursor&exclude_archived=true&types=public_channel,private_channel")
      .setHeader("Authorization" -> s"Bearer $token")
      .withProxy
      .execute[Either[UpstreamErrorResponse, SlackChannelListPage]]
      .flatMap:
        case Right(r) =>
          Future.successful(r)
        case Left(e)  =>
          logger.error(s"Slack API returned ${e.statusCode} for conversations.list: ${e.getMessage}", e)
          Future.failed(e)

  def listAllChannels()(using Materializer, HeaderCarrier): Future[Seq[SlackChannel]] =
    Source
      .unfoldAsync(Option(""): Option[String]):
        case None         => Future.successful(None)
        case Some(cursor) =>
          getSlackChannelsPage(cursor).map: result =>
            if result.nextCursor.isEmpty then Some((None, result))
            else Some((Some(result.nextCursor), result))
      .throttle(1, requestThrottle)
      .runFold(Seq.empty[SlackChannel]): (acc, page) =>
        acc ++ page.channels

  def createChannel(name: String)(using HeaderCarrier): Future[Option[SlackChannel]] =
    given Reads[SlackChannelResponse] = SlackChannelResponse.reads
    httpClientV2
      .post(url"$apiUrl/conversations.create")
      .setHeader("Authorization" -> s"Bearer $token")
      .withBody(Json.obj("name" -> name))
      .withProxy
      .execute[Either[UpstreamErrorResponse, SlackChannelResponse]]
      .flatMap:
        case Right(r) =>
          Future.successful(r.channel)
        case Left(e)  =>
          logger.error(s"Slack API returned ${e.statusCode} for conversations.create for $name: ${e.getMessage}", e)
          Future.failed(e)

  def inviteUsersToChannel(channelId: String, userIds: Seq[String])(using HeaderCarrier): Future[Unit] =
    if userIds.isEmpty then Future.unit
    else
      httpClientV2
        .post(url"$apiUrl/conversations.invite")
        .setHeader("Authorization" -> s"Bearer $token")
        .withBody(Json.obj("channel" -> channelId, "users" -> userIds.mkString(",")))
        .withProxy
        .execute[Either[UpstreamErrorResponse, Unit]]
        .flatMap:
          case Right(_) =>
            Future.unit
          case Left(e)  =>
            logger.error(s"Slack API returned ${e.statusCode} for conversations.invite for $channelId: ${e.getMessage}", e)
            Future.failed(e)

  private def getChannelMembersPage(channelId: String, cursor: String)(using HeaderCarrier): Future[SlackChannelMembersResponse] =
    given Reads[SlackChannelMembersResponse] = SlackChannelMembersResponse.reads
    httpClientV2
      .get(url"$apiUrl/conversations.members?channel=$channelId&limit=$limit&cursor=$cursor")
      .setHeader("Authorization" -> s"Bearer $token")
      .withProxy
      .execute[Either[UpstreamErrorResponse, SlackChannelMembersResponse]]
      .flatMap:
        case Right(r) =>
          Future.successful(r)
        case Left(e)  =>
          logger.error(s"Slack API returned ${e.statusCode} for conversations.members for $channelId: ${e.getMessage}", e)
          Future.failed(e)

  def listChannelMembers(channelId: String)(using Materializer, HeaderCarrier): Future[Seq[String]] =
    Source.unfoldAsync(Option(""): Option[String]):
      case None => Future.successful(None)
      case Some(cursor) =>
        getChannelMembersPage(channelId, cursor).map: result =>
          if result.nextCursor.isEmpty
          then Some((None, result.members))
          else Some((Some(result.nextCursor), result.members))
    .throttle(1, requestThrottle)
    .runFold(Seq.empty[String])(_ ++ _)

end SlackConnector

private final case class SlackUserListPage(
  members   : Seq[SlackUser],
  nextCursor: String
)
private object SlackUserListPage:
  given Reads[SlackUser] = SlackUser.apiReads
  val reads: Reads[SlackUserListPage] =
    ( (__ \ "members"                          ).read[Seq[SlackUser]]
    ~ (__ \ "response_metadata" \ "next_cursor").read[String]
    )(SlackUserListPage.apply)

final case class SlackChannel(id: String, name: String, isPrivate: Boolean)

object SlackChannel:
  val reads: Reads[SlackChannel] =
    ( (__ \ "id"         ).read[String]
      ~ (__ \ "name"       ).read[String]
      ~ (__ \ "is_private" ).read[Boolean]
    )(SlackChannel.apply)

private final case class SlackChannelListPage(channels: Seq[SlackChannel], nextCursor: String)

private object SlackChannelListPage:
  given Reads[SlackChannel] = SlackChannel.reads
  val reads: Reads[SlackChannelListPage] =
    ( (__ \ "channels"                         ).read[Seq[SlackChannel]]
    ~ (__ \ "response_metadata" \ "next_cursor").read[String]
    )(SlackChannelListPage.apply)

case class SlackChannelResponse(
  ok     : Boolean,
  channel: Option[SlackChannel],
  error  : Option[String]
)

object SlackChannelResponse:
  given Reads[SlackChannel] = SlackChannel.reads
  val reads: Reads[SlackChannelResponse] =
    ( (__ \ "ok"     ).read[Boolean]
    ~ (__ \ "channel").readNullable[SlackChannel]
    ~ (__ \ "error"  ).readNullable[String]
    )(SlackChannelResponse.apply)

case class SlackChannelMembersResponse(
  ok        : Boolean,
  members   : Seq[String],
  nextCursor: String,
  error     : Option[String]
)

object SlackChannelMembersResponse:
  val reads: Reads[SlackChannelMembersResponse] =
    ( (__ \ "ok"                               ).read[Boolean]
    ~ (__ \ "members"                          ).readNullable[Seq[String]].map(_.getOrElse(Seq.empty))
    ~ (__ \ "response_metadata" \ "next_cursor").read[String]
    ~ (__ \ "error"                            ).readNullable[String]
    )(SlackChannelMembersResponse.apply)

case class SlackUserResponse(
  ok  : Boolean,
  user: Option[SlackUser]
)

object SlackUserResponse:
  given Reads[SlackUser] = SlackUser.apiReads
  val reads: Reads[SlackUserResponse] =
    ( (__ \ "ok"  ).read[Boolean]
    ~ (__ \ "user").readNullable[SlackUser]
    )(SlackUserResponse.apply)

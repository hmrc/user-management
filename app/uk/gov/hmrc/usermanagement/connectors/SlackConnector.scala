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

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.Materializer
import play.api.Configuration
import play.api.libs.functional.syntax.*
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.usermanagement.model.SlackUser

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

@Singleton
class SlackConnector @Inject()(
  httpClientV2 : HttpClientV2,
  configuration: Configuration
)(using
  ExecutionContext
):
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

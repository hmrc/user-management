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
import play.api.libs.json.{Format, __}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.usermanagement.model.SlackUser

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

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

  private def getSlackUsersPage(cursor: String)(using HeaderCarrier): Future[SlackUserListPage] =
    given Format[SlackUserListPage] = SlackUserListPage.format
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
          then Some((None, cursor))
          else Some((Some(result.nextCursor), cursor))
    .throttle(1, 3.second) // https://api.slack.com/methods/users.list is API limit tier 2 which is 20 per minute
    .mapAsync(1)(cursor => getSlackUsersPage(cursor))
    .runFold(Seq.empty[SlackUser]): (acc, page) =>
      acc ++ page.members

end SlackConnector

private final case class SlackUserListPage(
  members   : Seq[SlackUser],
  nextCursor: String
)

private object SlackUserListPage:
  given Format[SlackUser] = SlackUser.format
  val format: Format[SlackUserListPage] =
    ( (__ \ "members"                          ).format[Seq[SlackUser]]
    ~ (__ \ "response_metadata" \ "next_cursor").format[String]
    )(SlackUserListPage.apply, pt => Tuple.fromProductTyped(pt))

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

import play.api.Configuration
import play.api.libs.functional.syntax.unlift
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, __}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.usermanagement.model.SlackUser

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlackConnector @Inject()(
  httpClientV2 : HttpClientV2,
  configuration: Configuration
)(implicit ec: ExecutionContext) {

  private lazy val apiUrl: String =
    configuration.get[String]("slack.apiUrl")

  private lazy val token: String =
    configuration.get[String]("slack.token")

  private lazy val limit: Int =
    configuration.get[Int]("slack.limit")

  private def getSlackUsersPage(cursor: String)(implicit hc: HeaderCarrier): Future[SlackUserListPage] = {
    implicit val sulpF: Format[SlackUserListPage] = SlackUserListPage.format
    httpClientV2
      .get(url"$apiUrl/users.list?limit=$limit&cursor=$cursor")
      .setHeader("Authorization" -> s"Bearer $token")
      .withProxy
      .execute[SlackUserListPage]
  }

  def getAllSlackUsers()(implicit hc: HeaderCarrier): Future[Seq[SlackUser]] = {
    def go(cursor: String, accMembers: Seq[SlackUser]): Future[Seq[SlackUser]] =
      getSlackUsersPage(cursor).flatMap { result =>
        val newMembers = accMembers ++ result.members
        if (result.nextCursor.isEmpty) Future.successful(newMembers)
          else go(result.nextCursor, newMembers)
      }
    go("", Seq.empty)
  }
}

private final case class SlackUserListPage(
  members   : Seq[SlackUser],
  nextCursor: String
)

private object SlackUserListPage {
  implicit val suF: Format[SlackUser] = SlackUser.format
  val format: Format[SlackUserListPage] =
    ( (__ \ "members").format[Seq[SlackUser]]
    ~ (__ \ "response_metadata" \ "next_cursor").format[String]
    )(SlackUserListPage.apply, unlift(SlackUserListPage.unapply))
}

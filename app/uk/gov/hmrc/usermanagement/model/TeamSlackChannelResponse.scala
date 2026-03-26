/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.usermanagement.model

import play.api.libs.functional.syntax.*
import play.api.libs.json.*

case class TeamSlackChannel(
  channelUrl: String,
  isPrivate : Boolean
)

object TeamSlackChannel:
  given format: OFormat[TeamSlackChannel] =
    ( (__ \ "channel_url").format[String]
    ~ (__ \ "is_private" ).format[Boolean]
    )(TeamSlackChannel.apply, Tuple.fromProductTyped)


case class TeamSlackChannelResponse(
  members          : Seq[Member],
  teamName         : String,
  description      : Option[String],
  documentation    : Option[String],
  slack            : Option[TeamSlackChannel],
  slackNotification: Option[TeamSlackChannel],
  platform         : Seq[String]
)

object TeamSlackChannelResponse:
  private def slackChannelFormat(field: String): OFormat[Option[TeamSlackChannel]] =
    val reads: Reads[Option[TeamSlackChannel]] =
      (__ \ field).readNullable[String].map(_.map(TeamSlackChannel(_, isPrivate = false)))
        .orElse((__ \ field).readNullable[TeamSlackChannel])

    val writes: OWrites[Option[TeamSlackChannel]] = OWrites:
      case Some(channel) => Json.obj(field -> Json.obj("channel_url" -> channel.channelUrl, "is_private"  -> channel.isPrivate))
      case None => Json.obj()

    OFormat(reads, writes)

  given format: OFormat[TeamSlackChannelResponse] =
    given Format[Member] = Member.format
    ( (__ \ "members"          ).format[Seq[Member]]
    ~ (__ \ "teamName"         ).format[String]
    ~ (__ \ "description"      ).formatNullable[String]
    ~ (__ \ "documentation"    ).formatNullable[String]
    ~ slackChannelFormat("slack")
    ~ slackChannelFormat("slackNotification")
    ~ (__ \ "platform"         ).format[Seq[String]]
    )(TeamSlackChannelResponse.apply, Tuple.fromProductTyped)

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

case class TeamSlackChannel(channelUrl: String, isPrivate: Boolean)

object TeamSlackChannel:
  given format: OFormat[TeamSlackChannel] =
    ((__ \ "channel_url").format[String]
    ~ (__ \ "is_private").format[Boolean]
    )(TeamSlackChannel.apply, (t: TeamSlackChannel) => (t.channelUrl, t.isPrivate))

case class TeamSlackChannelResponse(
                                     members:           Seq[Member],
                                     teamName:          String,
                                     description:       Option[String],
                                     documentation:     Option[String],
                                     slack:             Option[TeamSlackChannel],
                                     slackNotification: Option[TeamSlackChannel]
                                   )

object TeamSlackChannelResponse:
  import play.api.libs.functional.syntax.*

  given format: OFormat[TeamSlackChannelResponse] =
    given Format[Member] = Member.format
    def slackChannelFormat: OFormat[Option[TeamSlackChannel]] =
      val reads: Reads[Option[TeamSlackChannel]] =
        (__ \ "slack").readNullable[String].map(_.map(TeamSlackChannel(_, isPrivate = false)))
          .orElse((__ \ "slack").readNullable[TeamSlackChannel])
      val writes: OWrites[Option[TeamSlackChannel]] = OWrites {
        case Some(channel) => Json.obj("slack" -> Json.obj("channel_url" -> channel.channelUrl, "is_private" -> channel.isPrivate))
        case None => Json.obj() // This will omit the field entirely
      }
      OFormat(reads, writes)

    def slackNotificationFormat: OFormat[Option[TeamSlackChannel]] =
      val reads: Reads[Option[TeamSlackChannel]] =
        (__ \ "slackNotification").readNullable[String].map(_.map(TeamSlackChannel(_, isPrivate = false)))
          .orElse((__ \ "slackNotification").readNullable[TeamSlackChannel])
      val writes: OWrites[Option[TeamSlackChannel]] = OWrites {
        case Some(channel) => Json.obj("slackNotification" -> Json.obj("channel_url" -> channel.channelUrl, "is_private" -> channel.isPrivate))
        case None => Json.obj() // This will omit the field entirely
      }
      OFormat(reads, writes)

    (
    (__ \ "members").format[Seq[Member]] and
    (__ \ "teamName").format[String] and
    (__ \ "description").formatNullable[String] and
    (__ \ "documentation").formatNullable[String] and
    slackChannelFormat and
    slackNotificationFormat
    )(TeamSlackChannelResponse.apply, (r: TeamSlackChannelResponse) =>
    (r.members, r.teamName, r.description, r.documentation, r.slack, r.slackNotification)
    )

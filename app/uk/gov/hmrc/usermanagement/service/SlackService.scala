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

package uk.gov.hmrc.usermanagement.service

import cats.syntax.all.*
import org.apache.pekko.stream.Materializer
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.usermanagement.connectors.{SlackChannel, SlackConnector, UmpConnector}
import uk.gov.hmrc.usermanagement.model.{ChannelStatus, EditTeamDetails, SlackChannelType, Team}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlackService @Inject()(
  slackConnector: SlackConnector,
  umpConnector  : UmpConnector
)(using
  ExecutionContext
) extends Logging:

  def ensureChannelExistsAndSyncMembers(
    teams      : Seq[Team],
    channelType: SlackChannelType,
    testMode   : Boolean = false
  )(using Materializer, HeaderCarrier): Future[Unit] =

    for
      existingChannels <- slackConnector.listAllChannels()
      results          <- teams.foldLeftM(List.empty[(SlackChannel, ChannelStatus)]) { (acc, team) =>
        val baseName       = normaliseTeamName(team.teamName)
        val canonicalName  = channelType match
          case SlackChannelType.Main         => baseName
          case SlackChannelType.Notification => s"$baseName-alerts"
        val nameVariations = channelType match
          case SlackChannelType.Main         => generateTeamNameVariants(team.teamName)
          case SlackChannelType.Notification => generateTeamNameVariants(team.teamName).map(_ + "-alerts")

        val channelOpt     = existingChannels.find(c => nameVariations.exists(variant => c.name.equalsIgnoreCase(variant)))
        val emails         = team.members.map(_.primaryEmail.trim).filter(_.nonEmpty).distinct

        val result =
          for
            emailToUserId <- emails.foldLeftM(List.empty[(String, Option[String])]) { (accEmails, email) =>
                              slackConnector.lookupUserByEmail(email).map(userOpt => accEmails :+ (email, userOpt.map(_.id)))
                            }
            foundIds      =  emailToUserId.collect { case (_, Some(id)) => id }.distinct
            missingEmails =  emailToUserId.collect { case (email, None) => email }

            res           <-
              if foundIds.isEmpty then
                logger.warn(s"No Slack users found for team '${team.teamName}'. Channel will NOT be created or updated.")
                Future.successful((SlackChannel("NONE", canonicalName), ChannelStatus.Skipped))
              else
                for
                  (channel, status) <- channelOpt match
                                       case Some(found) =>
                                         logger.info(s"Found existing Slack channel '${found.name}' for team '${team.teamName}'")
                                         Future.successful((found, ChannelStatus.Found))

                                       case None if testMode =>
                                         logger.info(s"[TEST MODE] Would create Slack channel '$canonicalName' for team '${team.teamName}'")
                                         Future.successful((SlackChannel("FAKE-ID", canonicalName), ChannelStatus.Created))

                                       case None =>
                                         slackConnector.createChannel(canonicalName).flatMap:
                                           case Some(created) =>
                                             logger.info(s"Created Slack channel '${created.name}' for team '${team.teamName}'")
                                             Future.successful((created, ChannelStatus.Created))
                                           case None =>
                                             logger.warn(s"Failed to create Slack channel '$canonicalName', attempting to refetch...")
                                             slackConnector.listAllChannels().map(_.find(_.name.equalsIgnoreCase(canonicalName))).flatMap:
                                               case Some(found) => Future.successful((found, ChannelStatus.Found))
                                               case None => Future.failed(new RuntimeException(s"Unable to create or find Slack channel '$canonicalName'"))

                  _                 =  if missingEmails.nonEmpty then
                                         logger.warn(s"Could not find Slack users by email for team '${team.teamName}': ${missingEmails.mkString(", ")}")
                  existingMemberIds <- if channel.id == "FAKE-ID" then Future.successful(Seq.empty[String])
                                       else slackConnector.listChannelMembers(channel.id)
                  newMemberIds      =  foundIds.diff(existingMemberIds)
                  _                 =  if newMemberIds.isEmpty
                                         then logger.info(s"All Slack users for team '${team.teamName}' are already in channel '${channel.name}'")
                                       else if testMode then
                                         logger.info(s"[TEST MODE] Would invite ${newMemberIds.size} new Slack users to channel '${channel.name}' for team '${team.teamName}'")
                                       else
                                         logger.info(s"Inviting ${newMemberIds.size} new Slack users to channel '${channel.name}' for team '${team.teamName}'")
                  _                 <- if !testMode && newMemberIds.nonEmpty then
                                         slackConnector.inviteUsersToChannel(channel.id, newMemberIds)
                                       else
                                         Future.unit
                  _                 <- if !testMode then
                                         val editTeamDetails = channelType match
                                           case SlackChannelType.Main   =>
                                             EditTeamDetails(
                                               team              = team.teamName,
                                               description       = team.description,
                                               documentation     = team.documentation,
                                               slack             = Some(channel.name),
                                               slackNotification = team.slackNotification
                                             )
                                           case SlackChannelType.Notification => 
                                               EditTeamDetails(
                                                 team              = team.teamName,
                                                 description       = team.description,
                                                 documentation     = team.documentation,
                                                 slack             = team.slack,
                                                 slackNotification = Some(channel.name)
                                               )
                                         umpConnector.editTeamDetails(editTeamDetails)
                                       else
                                         Future.unit
                yield (channel, status)
          yield res

        result.recover { case e =>
          logger.error(s"Failed to sync Slack channel for team '${team.teamName}': ${e.getMessage}", e)
          (SlackChannel(canonicalName, "FAILED"), ChannelStatus.Failed)
        }.map(r => acc :+ r)
      }
    yield
      val summary      = results.groupBy(_._2).view.mapValues(_.size).toMap
      val createdCount = summary.getOrElse(ChannelStatus.Created, 0)
      val foundCount   = summary.getOrElse(ChannelStatus.Found, 0)
      val failedCount  = summary.getOrElse(ChannelStatus.Failed, 0)
      val skippedCount = summary.getOrElse(ChannelStatus.Skipped, 0)

      val mode = if testMode then "[TEST MODE]" else ""
      logger.info(s"$mode ${channelType.toString.toLowerCase} Slack channel sync complete: $createdCount created, $foundCount found, $failedCount failed, $skippedCount skipped.")

  private def normaliseTeamName(teamName: String): String =
    "team-" + teamName
      .replaceAll("\\s+", "-")
      .replaceAll("[^a-zA-Z0-9-]", "")
      .toLowerCase

  private def generateTeamNameVariants(teamName: String): Set[String] =
    val cleaned = teamName.trim

    val camelSplit = cleaned.replaceAll("([a-z])([A-Z])", "$1-$2")
    val spaceSplit = cleaned.replaceAll("\\s+", "-")
    val camelAndSpaceSplit = camelSplit.replaceAll("\\s+", "-")

    val baseForms = Set(cleaned, camelSplit, spaceSplit, camelAndSpaceSplit)

    val normalised = baseForms.map:
      _.replaceAll("[^a-zA-Z0-9-]", "")
        .toLowerCase
        .stripPrefix("team-") // avoid double prefix if already there
        .nn

    val extraVariants =
      normalised.flatMap { n =>
        val parts = n.split("-").filter(_.nonEmpty)
        (1 until parts.length)
          .foldLeft(Set(parts.head)) { (acc, idx) =>
            val next = parts(idx)
            acc.flatMap(a => Set(a + next, a + "-" + next))
          }
      }

    (normalised ++ extraVariants).map("team-" + _)

end SlackService

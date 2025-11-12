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

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.usermanagement.connectors.{SlackChannel, SlackConnector, UmpConnector}
import uk.gov.hmrc.usermanagement.model.{EditTeamDetails, Member, SlackChannelType, SlackUser, Team}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SlackServiceSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar
    with IntegrationPatience:

  private given HeaderCarrier = HeaderCarrier()
  private given ActorSystem   = ActorSystem("test")

  "ensureChannelExistsAndSyncMembers" should:

    "create a Main Slack channel if one does not exist, invite all found users, and update UMP" in new SlackServiceSetup:
      val team = Team(
        teamName          = "PlatOps",
        description       = Some("desc"),
        documentation     = Some("docs"),
        members           = Seq(
          Member("joe.bloggs", Some("Joe Bloggs"), "joe.bloggs@gmail.com", "team_admin", false),
          Member("jane.doe"  , Some("Jane Doe")  , "jane.doe@gmail.com"  , "user"      , false)
        ),
        slack             = None,
        slackNotification = None
      )

      when(slackConnector.listAllChannels()(using any[Materializer], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))

      when(slackConnector.createChannel(eqTo("team-platops"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackChannel("C123", "team-platops"))))

      when(slackConnector.lookupUserByEmail(eqTo("joe.bloggs@gmail.com"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackUser(Some("joe.bloggs@gmail.com"), "U1", "joe.bloggs", false, false))))

      when(slackConnector.lookupUserByEmail(eqTo("jane.doe@gmail.com"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackUser(Some("jane.doe@gmail.com"), "U2", "jane.doe", false, false))))

      when(slackConnector.listChannelMembers(eqTo("C123"))(using any[Materializer], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))

      when(slackConnector.inviteUsersToChannel(eqTo("C123"), eqTo(Seq("U1", "U2")))(using any[HeaderCarrier]))
        .thenReturn(Future.unit)

      when(umpConnector.editTeamDetails(any[EditTeamDetails])(using any[HeaderCarrier]))
        .thenReturn(Future.unit)

      service.ensureChannelExistsAndSyncMembers(Seq(team), SlackChannelType.Main).futureValue

      verify(slackConnector).createChannel(eqTo("team-platops"))(using any[HeaderCarrier])
      verify(slackConnector).inviteUsersToChannel(eqTo("C123"), eqTo(Seq("U1", "U2")))(using any[HeaderCarrier])
      verify(umpConnector).editTeamDetails(
        eqTo(EditTeamDetails(
          team              = "PlatOps",
          description       = Some("desc"),
          documentation     = Some("docs"),
          slack             = Some("team-platops"),
          slackNotification = None
        )))(using any[HeaderCarrier])

    "create a Notification Slack channel if one does not exist, invite all found users, and update UMP" in new SlackServiceSetup:
      val team = Team(
        teamName          = "PlatOps",
        description       = Some("desc"),
        documentation     = Some("docs"),
        members = Seq(
          Member("joe.bloggs", Some("Joe Bloggs"), "joe.bloggs@gmail.com", "team_admin", false),
          Member("jane.doe", Some("Jane Doe"), "jane.doe@gmail.com", "user", false)
        ),
        slack             = None,
        slackNotification = None
      )

      when(slackConnector.listAllChannels()(using any[Materializer], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))

      when(slackConnector.createChannel(eqTo("team-platops-alerts"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackChannel("C123", "team-platops-alerts"))))

      when(slackConnector.lookupUserByEmail(eqTo("joe.bloggs@gmail.com"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackUser(Some("joe.bloggs@gmail.com"), "U1", "joe.bloggs", false, false))))

      when(slackConnector.lookupUserByEmail(eqTo("jane.doe@gmail.com"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackUser(Some("jane.doe@gmail.com"), "U2", "jane.doe", false, false))))

      when(slackConnector.listChannelMembers(eqTo("C123"))(using any[Materializer], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))

      when(slackConnector.inviteUsersToChannel(eqTo("C123"), eqTo(Seq("U1", "U2")))(using any[HeaderCarrier]))
        .thenReturn(Future.unit)

      when(umpConnector.editTeamDetails(any[EditTeamDetails])(using any[HeaderCarrier]))
        .thenReturn(Future.unit)

      service.ensureChannelExistsAndSyncMembers(Seq(team), SlackChannelType.Notification).futureValue

      verify(slackConnector).createChannel(eqTo("team-platops-alerts"))(using any[HeaderCarrier])
      verify(slackConnector).inviteUsersToChannel(eqTo("C123"), eqTo(Seq("U1", "U2")))(using any[HeaderCarrier])
      verify(umpConnector).editTeamDetails(
        eqTo(EditTeamDetails(
          team              = "PlatOps",
          description       = Some("desc"),
          documentation     = Some("docs"),
          slack             = None,
          slackNotification = Some("team-platops-alerts")
        )))(using any[HeaderCarrier])

    "use existing main channel if found, and only add missing members" in new SlackServiceSetup:
      val team = Team(
        teamName          = "PlatOps",
        description       = None,
        documentation     = None,
        members           = Seq(
          Member("joe.bloggs"   , Some("Joe Bloggs")   , "joe.bloggs@gmail.com"   , "team_admin", false),
          Member("jane.doe"     , Some("Jane Doe")     , "jane.doe@gmail.com"     , "user"      , false),
          Member("existing.user", Some("Existing User"), "existing.user@gmail.com", "user"      , false)
        ),
        slack             = None,
        slackNotification = None
      )

      when(slackConnector.listAllChannels()(using any[Materializer], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(SlackChannel("C123", "team-platops"))))

      when(slackConnector.lookupUserByEmail(eqTo("joe.bloggs@gmail.com"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackUser(Some("joe.bloggs@gmail.com"), "U1", "joe.bloggs", false, false))))

      when(slackConnector.lookupUserByEmail(eqTo("jane.doe@gmail.com"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackUser(Some("jane.doe@gmail.com"), "U2", "jane.doe", false, false))))

      when(slackConnector.lookupUserByEmail(eqTo("existing.user@gmail.com"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackUser(Some("existing.user@gmail.com"), "U3", "existing.user", false, false))))

      when(slackConnector.listChannelMembers(eqTo("C123"))(using any[Materializer], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq("U3")))

      when(slackConnector.inviteUsersToChannel(eqTo("C123"), eqTo(Seq("U1", "U2")))(using any[HeaderCarrier]))
        .thenReturn(Future.unit)

      when(umpConnector.editTeamDetails(any[EditTeamDetails])(using any[HeaderCarrier]))
        .thenReturn(Future.unit)

      service.ensureChannelExistsAndSyncMembers(Seq(team), SlackChannelType.Main).futureValue

      verify(slackConnector, never).createChannel(any[String])(using any[HeaderCarrier])
      verify(slackConnector).inviteUsersToChannel(eqTo("C123"), eqTo(Seq("U1", "U2")))(using any[HeaderCarrier])
      verify(umpConnector).editTeamDetails(
        eqTo(EditTeamDetails(
          team              = "PlatOps",
          description       = None,
          documentation     = None,
          slack             = Some("team-platops"),
          slackNotification = None
        ))
      )(using any[HeaderCarrier])

    "use existing notification channel if found, and only add missing members" in new SlackServiceSetup:
      val team = Team(
        teamName          = "PlatOps",
        description       = None,
        documentation     = None,
        members           = Seq(
          Member("joe.bloggs", Some("Joe Bloggs"), "joe.bloggs@gmail.com", "team_admin", false),
          Member("jane.doe", Some("Jane Doe"), "jane.doe@gmail.com", "user", false),
          Member("existing.user", Some("Existing User"), "existing.user@gmail.com", "user", false)
        ),
        slack             = None,
        slackNotification = None
      )

      when(slackConnector.listAllChannels()(using any[Materializer], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(SlackChannel("C123", "team-platops-alerts"))))

      when(slackConnector.lookupUserByEmail(eqTo("joe.bloggs@gmail.com"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackUser(Some("joe.bloggs@gmail.com"), "U1", "joe.bloggs", false, false))))

      when(slackConnector.lookupUserByEmail(eqTo("jane.doe@gmail.com"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackUser(Some("jane.doe@gmail.com"), "U2", "jane.doe", false, false))))

      when(slackConnector.lookupUserByEmail(eqTo("existing.user@gmail.com"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(SlackUser(Some("existing.user@gmail.com"), "U3", "existing.user", false, false))))

      when(slackConnector.listChannelMembers(eqTo("C123"))(using any[Materializer], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq("U3")))

      when(slackConnector.inviteUsersToChannel(eqTo("C123"), eqTo(Seq("U1", "U2")))(using any[HeaderCarrier]))
        .thenReturn(Future.unit)

      when(umpConnector.editTeamDetails(any[EditTeamDetails])(using any[HeaderCarrier]))
        .thenReturn(Future.unit)

      service.ensureChannelExistsAndSyncMembers(Seq(team), SlackChannelType.Notification).futureValue

      verify(slackConnector, never).createChannel(any[String])(using any[HeaderCarrier])
      verify(slackConnector).inviteUsersToChannel(eqTo("C123"), eqTo(Seq("U1", "U2")))(using any[HeaderCarrier])
      verify(umpConnector).editTeamDetails(
        eqTo(EditTeamDetails(
          team              = "PlatOps",
          description       = None,
          documentation     = None,
          slack             = None,
          slackNotification = Some("team-platops-alerts")
        ))
      )(using any[HeaderCarrier])

    "log and recover when Slack channel creation fails" in new SlackServiceSetup:
      val team = Team(members = Nil, teamName = "PlatOps", description = None, documentation = None, slack = None, slackNotification = None)

      when(slackConnector.listAllChannels()(using any[Materializer], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))
      when(slackConnector.createChannel(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      noException shouldBe thrownBy(service.ensureChannelExistsAndSyncMembers(Seq(team), SlackChannelType.Main).futureValue)
      noException shouldBe thrownBy(service.ensureChannelExistsAndSyncMembers(Seq(team), SlackChannelType.Notification).futureValue)

end SlackServiceSpec

trait SlackServiceSetup:
  private given ActorSystem = ActorSystem("test")

  val slackConnector: SlackConnector = mock[SlackConnector]
  val umpConnector  : UmpConnector   = mock[UmpConnector]
  val config        : Configuration  = Configuration(ConfigFactory.load("application.conf"))

  val service       : SlackService   = SlackService(slackConnector, umpConnector, config)

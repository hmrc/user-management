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

package uk.gov.hmrc.usermanagement.controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, verify, when}
import org.scalactic.Prettifier.default
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.OK
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import uk.gov.hmrc.usermanagement.connectors.{SlackChannel, SlackConnector, UmpConnector}
import uk.gov.hmrc.usermanagement.model.{Member, Team}
import uk.gov.hmrc.usermanagement.persistence.{SlackChannelCacheRepository, TeamsRepository, UsersRepository}
import uk.gov.hmrc.usermanagement.service.UserAccessService

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserManagementControllerSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar:

  private given actorSystem   : ActorSystem  = ActorSystem("test")
  private given materializer  : Materializer = SystemMaterializer(actorSystem).materializer

  private val cc: ControllerComponents                 = stubMessagesControllerComponents()
  private val mockUmpConnector: UmpConnector           = mock[UmpConnector]
  private val mockUserAccessService: UserAccessService = mock[UserAccessService]
  private val mockUsersRepo: UsersRepository           = mock[UsersRepository]
  private val mockTeamsRepo                            = mock[TeamsRepository]
  private val mockSlackConnector                       = mock[SlackConnector]
  private val mockSlackChannelCacheRepo                = mock[SlackChannelCacheRepository]

  private def controller: UserManagementController =
    new UserManagementController(cc, mockUmpConnector, mockUserAccessService, mockUsersRepo, mockTeamsRepo, mockSlackChannelCacheRepo)

  "getAllTeams" should :
    "return teams with slack and slackNotification channel privacy using cache and preserve members when includeNonHuman=true" in :
      val teams = Seq(
        Team(
          members = Seq(
            Member("u1", Some("U One"), "u1@email", "role", isNonHuman = false),
            Member("bot", None, "bot@email", "role", isNonHuman = true)
          ),
          teamName          = "TeamA",
          description       = Some("d"),
          documentation     = Some("doc"),
          slack             = Some("team-teama"),
          slackNotification = Some("alerts-teama")
        )
      )

      when(mockTeamsRepo.findAll()).thenReturn(Future.successful(teams))
      // Cache hit for both
      when(mockSlackChannelCacheRepo.findByChannelUrl(eqTo("team-teama"))).thenReturn(Future.successful(Some(uk.gov.hmrc.usermanagement.model.SlackChannelCache("team-teama", isPrivate = false, Instant.now()))))
      when(mockSlackChannelCacheRepo.findByChannelUrl(eqTo("alerts-teama"))).thenReturn(Future.successful(Some(uk.gov.hmrc.usermanagement.model.SlackChannelCache("alerts-teama", isPrivate = true, Instant.now()))))

      val result = controller.getAllTeams(includeNonHuman = true)(FakeRequest())
      status(result) shouldBe OK
      val json: JsValue = contentAsJson(result)

      val arr = json.as[JsArray].value
      arr should have size 1
      val teamJson = arr.head
      (teamJson \ "teamName").as[String] shouldBe "TeamA"
      // members preserved
      (teamJson \ "members").as[JsArray].value.length shouldBe 2
      // slack object present
      (teamJson \ "slack").as[JsValue] shouldBe Json.obj("channel_url" -> "team-teama", "is_private" -> false)
      // slackNotification object present
      (teamJson \ "slackNotification").as[JsValue] shouldBe Json.obj("channel_url" -> "alerts-teama", "is_private" -> true)

      // No need to call listAllChannels on cache hit
      verify(mockSlackConnector, never).listAllChannels()(using any[Materializer], any[HeaderCarrier])


    "filter non-human members when includeNonHuman=false" in :
      val teams = Seq(
        Team(
          members = Seq(
            Member("human", Some("Human"), "h@email", "role", isNonHuman = false),
            Member("bot"  , None         , "b@email", "role", isNonHuman = true)
          ),
          teamName          = "TeamB",
          description       = None,
          documentation     = None,
          slack             = Some("team-teamb"),
          slackNotification = None
        )
      )

      when(mockTeamsRepo.findAll()).thenReturn(Future.successful(teams))
      when(mockSlackChannelCacheRepo.findByChannelUrl(eqTo("team-teamb"))).thenReturn(Future.successful(Some(uk.gov.hmrc.usermanagement.model.SlackChannelCache("team-teamb", isPrivate = false, Instant.now()))))

      val result = controller.getAllTeams(includeNonHuman = false)(FakeRequest())
      status(result) shouldBe OK
      val arr = contentAsJson(result).as[JsArray].value
      (arr.head \ "members").as[JsArray].value.length shouldBe 1

  "getTeamByTeamName" should :
    "return a team with channel privacy from cache when present" in :
      val team = Team(
        members           = Seq(Member("u1", None, "u1@email", "role", isNonHuman = false)),
        teamName          = "TeamC",
        description       = None,
        documentation     = None,
        slack             = Some("team-teamc"),
        slackNotification = Some("alerts-teamc")
      )

      when(mockTeamsRepo.findByTeamName(eqTo("TeamC"))).thenReturn(Future.successful(Some(team)))
      when(mockSlackChannelCacheRepo.findByChannelUrl(eqTo("team-teamc"))).thenReturn(Future.successful(Some(uk.gov.hmrc.usermanagement.model.SlackChannelCache("team-teamc", isPrivate = false, Instant.now()))))
      when(mockSlackChannelCacheRepo.findByChannelUrl(eqTo("alerts-teamc"))).thenReturn(Future.successful(Some(uk.gov.hmrc.usermanagement.model.SlackChannelCache("alerts-teamc", isPrivate = true, Instant.now()))))

      val result = controller.getTeamByTeamName("TeamC", includeNonHuman = true)(FakeRequest())
      status(result) shouldBe OK
      val json = contentAsJson(result)
      (json \ "teamName").as[String] shouldBe "TeamC"
      (json \ "slack").as[JsValue] shouldBe Json.obj("channel_url" -> "team-teamc", "is_private" -> false)
      (json \ "slackNotification").as[JsValue] shouldBe Json.obj("channel_url" -> "alerts-teamc", "is_private" -> true)
      verify(mockSlackConnector, never).listAllChannels()(using any[Materializer], any[HeaderCarrier])

  "getAvailablePlatforms" should:
    "return 200 OK with the list of platforms in JSON format" in:
      val expectedPlatforms = Seq("MDTP", "PEGA", "Salesforce")
      when(mockUmpConnector.getAvailablePlatforms()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(expectedPlatforms))

      val result = controller.getAvailablePlatforms.apply(FakeRequest())

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(expectedPlatforms)

    "return 200 OK with empty values if no platforms are found" in:
      val expectedPlatforms = Seq.empty[String]
      when(mockUmpConnector.getAvailablePlatforms()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(expectedPlatforms))

      val result = controller.getAvailablePlatforms.apply(FakeRequest())

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(expectedPlatforms)

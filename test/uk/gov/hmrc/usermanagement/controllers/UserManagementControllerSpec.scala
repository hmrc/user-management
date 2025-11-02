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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserManagementControllerSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar {

  private given HeaderCarrier = HeaderCarrier()
  private given actorSystem   : ActorSystem  = ActorSystem("test")
  private given materializer  : Materializer = SystemMaterializer(actorSystem).materializer

  private def controller(
    teamsRepository: TeamsRepository,
    slackConnector: SlackConnector,
    slackCache: SlackChannelCacheRepository
  ): UserManagementController = {
    val cc: ControllerComponents = stubMessagesControllerComponents()
    val dummyUmp: UmpConnector = mock[UmpConnector]
    val dummyAccess: UserAccessService = mock[UserAccessService]
    val dummyUsersRepo: UsersRepository = mock[UsersRepository]
    new UserManagementController(
      cc,
      dummyUmp,
      dummyAccess,
      dummyUsersRepo,
      teamsRepository,
      slackConnector,
      slackCache
    )
  }

  "getAllTeams" should {
    "return teams with slack and slackNotification channel privacy using cache and preserve members when includeNonHuman=true" in {
      val teamsRepo = mock[TeamsRepository]
      val slack = mock[SlackConnector]
      val cache = mock[SlackChannelCacheRepository]

      val teams = Seq(
        Team(
          members = Seq(
            Member("u1", Some("U One"), "u1@email", "role", isNonHuman = false),
            Member("bot", None, "bot@email", "role", isNonHuman = true)
          ),
          teamName = "TeamA",
          description = Some("d"),
          documentation = Some("doc"),
          slack = Some("team-teama"),
          slackNotification = Some("alerts-teama")
        )
      )

      when(teamsRepo.findAll()).thenReturn(Future.successful(teams))
      // Cache hit for both
      when(cache.findByChannelName(eqTo("team-teama"))).thenReturn(Future.successful(Some(uk.gov.hmrc.usermanagement.model.SlackChannelCache("team-teama", isPrivate = false))))
      when(cache.findByChannelName(eqTo("alerts-teama"))).thenReturn(Future.successful(Some(uk.gov.hmrc.usermanagement.model.SlackChannelCache("alerts-teama", isPrivate = true))))

      val ctrl = controller(teamsRepo, slack, cache)

      val result = ctrl.getAllTeams(includeNonHuman = true)(FakeRequest())
      status(result) shouldBe 200
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
      verify(slack, never).listAllChannels()(using any[Materializer], any[HeaderCarrier])
    }

    "filter non-human members when includeNonHuman=false" in {
      val teamsRepo = mock[TeamsRepository]
      val slack = mock[SlackConnector]
      val cache = mock[SlackChannelCacheRepository]

      val teams = Seq(
        Team(
          members = Seq(
            Member("human", Some("Human"), "h@email", "role", isNonHuman = false),
            Member("bot"  , None            , "b@email", "role", isNonHuman = true)
          ),
          teamName = "TeamB",
          description = None,
          documentation = None,
          slack = Some("team-teamb"),
          slackNotification = None
        )
      )

      when(teamsRepo.findAll()).thenReturn(Future.successful(teams))
      when(cache.findByChannelName(eqTo("team-teamb"))).thenReturn(Future.successful(Some(uk.gov.hmrc.usermanagement.model.SlackChannelCache("team-teamb", isPrivate = false))))

      val ctrl = controller(teamsRepo, slack, cache)

      val result = ctrl.getAllTeams(includeNonHuman = false)(FakeRequest())
      status(result) shouldBe 200
      val arr = contentAsJson(result).as[JsArray].value
      (arr.head \ "members").as[JsArray].value.length shouldBe 1
    }
  }

  "getTeamByTeamName" should {
    "return a team with channel privacy from cache when present" in {
      val teamsRepo = mock[TeamsRepository]
      val slack = mock[SlackConnector]
      val cache = mock[SlackChannelCacheRepository]

      val team = Team(
        members = Seq(Member("u1", None, "u1@email", "role", isNonHuman = false)),
        teamName = "TeamC",
        description = None,
        documentation = None,
        slack = Some("team-teamc"),
        slackNotification = Some("alerts-teamc")
      )

      when(teamsRepo.findByTeamName(eqTo("TeamC"))).thenReturn(Future.successful(Some(team)))
      when(cache.findByChannelName(eqTo("team-teamc"))).thenReturn(Future.successful(Some(uk.gov.hmrc.usermanagement.model.SlackChannelCache("team-teamc", isPrivate = false))))
      when(cache.findByChannelName(eqTo("alerts-teamc"))).thenReturn(Future.successful(Some(uk.gov.hmrc.usermanagement.model.SlackChannelCache("alerts-teamc", isPrivate = true))))

      val ctrl = controller(teamsRepo, slack, cache)
      val result = ctrl.getTeamByTeamName("TeamC", includeNonHuman = true)(FakeRequest())
      status(result) shouldBe 200
      val json = contentAsJson(result)
      (json \ "teamName").as[String] shouldBe "TeamC"
      (json \ "slack").as[JsValue] shouldBe Json.obj("channel_url" -> "team-teamc", "is_private" -> false)
      (json \ "slackNotification").as[JsValue] shouldBe Json.obj("channel_url" -> "alerts-teamc", "is_private" -> true)
      verify(slack, never).listAllChannels()(using any[Materializer], any[HeaderCarrier])
    }

    "fetch from Slack and cache when not present in cache" in {
      val teamsRepo = mock[TeamsRepository]
      val slack = mock[SlackConnector]
      val cache = mock[SlackChannelCacheRepository]

      val team = Team(
        members = Nil,
        teamName = "TeamD",
        description = None,
        documentation = None,
        slack = Some("team-teamd"),
        slackNotification = Some("alerts-teamd")
      )

      when(teamsRepo.findByTeamName(eqTo("TeamD"))).thenReturn(Future.successful(Some(team)))
      when(cache.findByChannelName(any[String])).thenReturn(Future.successful(None))
      when(slack.listAllChannels()(using any[Materializer], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(
          SlackChannel("CID1", "team-teamd", isPrivate = false),
          SlackChannel("CID2", "alerts-teamd", isPrivate = true)
        )))
      when(cache.upsert(eqTo("team-teamd"), eqTo(false))).thenReturn(Future.unit)
      when(cache.upsert(eqTo("alerts-teamd"), eqTo(true))).thenReturn(Future.unit)

      val ctrl = controller(teamsRepo, slack, cache)
      val result = ctrl.getTeamByTeamName("TeamD", includeNonHuman = true)(FakeRequest())
      status(result) shouldBe 200
      val json = contentAsJson(result)
      (json \ "slack").as[JsValue] shouldBe Json.obj("channel_url" -> "team-teamd", "is_private" -> false)
      (json \ "slackNotification").as[JsValue] shouldBe Json.obj("channel_url" -> "alerts-teamd", "is_private" -> true)
      verify(cache).upsert(eqTo("team-teamd"), eqTo(false))
      verify(cache).upsert(eqTo("alerts-teamd"), eqTo(true))
    }
  }
}

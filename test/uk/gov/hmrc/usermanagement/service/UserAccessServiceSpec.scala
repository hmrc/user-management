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

package uk.gov.hmrc.usermanagement.service

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, verifyNoInteractions, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.usermanagement.connectors.UmpConnector
import uk.gov.hmrc.usermanagement.model.{UserAccess, UserWithAccess}
import uk.gov.hmrc.usermanagement.persistence.UserAccessRepository

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserAccessServiceSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar:

  private given HeaderCarrier = HeaderCarrier()

  "getUserAccess" when:
    "the user already exists in the UserAccess repository" should :
      "return the user access and not call UMP" in new TestSetup:
        when(userAccessRepository.findByUsername(any[String]))
          .thenReturn(Future.successful(Some(userWithAccess)))

        val res = service.getUserAccess(username).futureValue

        res shouldBe Some(userWithAccess.access)
        verifyNoInteractions(umpConnector)

    "the user does not already exist in the UserAccess repository" should:
      "update the UserAccess repository based on the data received from UMP and return the access" in new TestSetup:
        when(userAccessRepository.findByUsername(any[String]))
          .thenReturn(Future.successful(None))

        when(umpConnector.getUserAccess(any[String])(using any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(userWithAccess.access)))

        val res = service.getUserAccess(username).futureValue

        res shouldBe Some(userWithAccess.access)
        verify(userAccessRepository).put(any[UserWithAccess])

    "the user does not already exist in the UserAccess repository or in UMP" should :
      "return None" in new TestSetup:
        when(userAccessRepository.findByUsername(any[String]))
          .thenReturn(Future.successful(None))

        when(umpConnector.getUserAccess(any[String])(using any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        val res = service.getUserAccess(username).futureValue

        res shouldBe None

end UserAccessServiceSpec

trait TestSetup:
  val umpConnector: UmpConnector = mock[UmpConnector]
  val userAccessRepository: UserAccessRepository = mock[UserAccessRepository]

  val service: UserAccessService = UserAccessService(umpConnector, userAccessRepository)

  val username = "Joe Bloggs"

  val userWithAccess: UserWithAccess =
    UserWithAccess(
      username = username,
      access = UserAccess(vpn = true, jira = true, confluence = true, devTools = true, googleApps = true),
      createdAt = Instant.MAX
    )

  when(userAccessRepository.put(any[UserWithAccess]))
    .thenReturn(Future.unit)

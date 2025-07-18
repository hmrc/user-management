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

import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.usermanagement.connectors.UmpConnector
import uk.gov.hmrc.usermanagement.model.{UserAccess, UserWithAccess}
import uk.gov.hmrc.usermanagement.persistence.UserAccessRepository

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserAccessService @Inject()(
  umpConnector        : UmpConnector,
  userAccessRepository: UserAccessRepository
)(using
  ExecutionContext
) extends Logging:

  def getUserAccess(username: String)(using HeaderCarrier): Future[Option[UserAccess]] =
    userAccessRepository.findByUsername(username).flatMap: // has a short cache
      case Some(userWithAccess) =>
        Future.successful(Some(userWithAccess.access))
      case None =>
        umpConnector.getUserAccess(username).flatMap:
          case Some(userAccess) =>
            userAccessRepository.put(UserWithAccess(username, userAccess, Instant.now())).map(_ => Some(userAccess))
          case None =>
            Future.successful(None)

  def manageVpnAccess(username: String, enableVpn: Boolean)(using HeaderCarrier): Future[Unit] =
    umpConnector.manageVpnAccess(username, enableVpn) // Async process

  def manageDevToolsAccess(username: String, enableDevTools: Boolean)(using HeaderCarrier): Future[Unit] =
    umpConnector.manageDevToolsAccess(username, enableDevTools) // Async process

end UserAccessService

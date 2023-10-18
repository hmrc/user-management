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

package uk.gov.hmrc.usermanagement.config

import play.api.Configuration

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration

@Singleton
class SchedulerConfig @Inject() (configuration: Configuration) {

  val enabledKey: String              = "scheduler.dataRefresh.enabled"
  val enabled: Boolean                = configuration.get[Boolean](enabledKey)
  val frequency: FiniteDuration       = configuration.get[FiniteDuration]("scheduler.dataRefresh.interval")
  val initialDelay: FiniteDuration    = configuration.get[FiniteDuration]("scheduler.dataRefresh.initialDelay")
  val requestThrottle: FiniteDuration = configuration.get[FiniteDuration]("scheduler.dataRefresh.requestThrottle")
}


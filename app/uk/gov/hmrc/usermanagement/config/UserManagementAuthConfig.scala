package uk.gov.hmrc.usermanagement.config

import play.api.Configuration

import javax.inject.Inject
import scala.concurrent.duration.Duration

class UserManagementAuthConfig @Inject()(configuration: Configuration){

  val authEnabled: Boolean = configuration.get[Boolean]("ump.auth.enabled")
  lazy val tokenTTL: Duration = configuration.get[Duration]("ump.auth.tokenTTL")

  lazy val username: String = configuration.get[String]("ump.auth.username")
  lazy val password: String = configuration.get[String]("ump.auth.password")

}

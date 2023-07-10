package uk.gov.hmrc.usermanagement.config

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class UserManagementPortalConfig @Inject() (servicesConfig: ServicesConfig) {

  private def getConfString(key: String): String =
    servicesConfig.getConfString(key, sys.error(s"Could not find config '$key'"))

  lazy val userManagementBaseUrl : String = getConfString("user-management.url")
  lazy val userManagementLoginUrl: String = getConfString("user-management.loginUrl")

}


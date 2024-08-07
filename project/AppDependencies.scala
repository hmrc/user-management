import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.0.0"
  private val hmrcMongoVersion = "2.1.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-30"         % hmrcMongoVersion,
    ehcache,
    "org.typelevel"           %% "cats-core"                  % "2.10.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion  % Test,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongoVersion  % Test,
    "org.scalatestplus"       %% "mockito-4-11"               % "3.2.18.0"        % Test,
  )
}

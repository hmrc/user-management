import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.17.0"
  private val hmrcMongoVersion = "2.7.0"

  val compile = Seq(
    caffeine,
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-30"         % hmrcMongoVersion,
    "org.typelevel"           %% "cats-core"                  % "2.13.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion  % Test,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongoVersion  % Test
  )
}

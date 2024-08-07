import play.sbt.PlayImport.PlayKeys.playDefaultPort

lazy val microservice = Project("user-management", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion        := 0,
    scalaVersion        := "3.3.3",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s",
    playDefaultPort := 8878,

  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)
  .disablePlugins(JUnitXmlReportPlugin)

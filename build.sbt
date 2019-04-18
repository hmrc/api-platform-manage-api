import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings.targetJvm
import uk.gov.hmrc.SbtArtifactory.autoImport.makePublicallyAvailableOnBintray
import uk.gov.hmrc.{SbtArtifactory, SbtAutoBuildPlugin}
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

lazy val appName = "api-platform-manage-api"
lazy val appDependencies: Seq[ModuleID] = compileDependencies ++ testDependencies

lazy val compileDependencies = Seq(
  "uk.gov.hmrc" %% "aws-gateway-proxied-request-lambda" % "0.6.0",
  "software.amazon.awssdk" % "apigateway" % "2.5.13",
  "io.swagger" % "swagger-parser" % "1.0.42"
)

lazy val testScope: String = "test"

lazy val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.0.5" % testScope,
  "com.stephenn" %% "scalatest-json-jsonassert" % "0.0.3" % testScope,
  "org.pegdown" % "pegdown" % "1.6.0" % testScope,
  "org.mockito" % "mockito-core" % "2.25.1" % testScope
)

lazy val library = (project in file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
  .settings(
    scalaVersion := "2.11.11",
    name := appName,
    majorVersion := 0,
    makePublicallyAvailableOnBintray := true,
    targetJvm := "jvm-1.8",
    crossScalaVersions := Seq("2.11.11"),
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= appDependencies,
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases")
    )
  )

// Coverage configuration
coverageMinimum := 90
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;uk.gov.hmrc.BuildInfo"

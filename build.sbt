import sbt.Keys._
import sbt._

lazy val appName = "api-platform-manage-api"
lazy val appDependencies: Seq[ModuleID] = compileDependencies ++ testDependencies

lazy val jacksonVersion = "2.19.0"

lazy val compileDependencies = Seq(
  "io.github.mkotsur"            %% "aws-lambda-scala"     % "0.3.0",
  "com.fasterxml.jackson.core"    % "jackson-databind"     % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "software.amazon.awssdk"        % "apigateway"           % "2.31.59",
  "io.swagger"                    % "swagger-parser"       % "1.0.75"
)

lazy val testDependencies = Seq(
  "org.scalatest"        %% "scalatest"                 % "3.2.18",
  "com.vladsch.flexmark"  % "flexmark-all"              % "0.64.8",
  "com.stephenn"         %% "scalatest-json-jsonassert" % "0.2.5",
  "org.mockito"          %% "mockito-scala-scalatest"   % "1.17.29"
).map(_ % Test)

lazy val library = (project in file("."))
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    scalaVersion := "2.13.16",
    name := appName,
    majorVersion := 0,
    isPublicArtefact := true,
    libraryDependencies ++= appDependencies
  )

// Coverage configuration
coverageMinimumStmtTotal := 90
coverageMinimumBranchTotal := 90
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;uk.gov.hmrc.BuildInfo"

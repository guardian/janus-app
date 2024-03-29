import play.sbt.PlayImport.PlayKeys._
import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd
import sbt.Keys._
import sbt.{addCompilerPlugin, _}
import ReleaseTransformations._

ThisBuild / organization := "com.gu"
ThisBuild / licenses := Seq(
  "Apache V2" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")
)
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/guardian/janus-app"),
    "scm:git@github.com:guardian/janus-app"
  )
)
ThisBuild / homepage := scmInfo.value.map(_.browseUrl)
ThisBuild / developers := List(
  Developer(
    id = "guardian",
    name = "Guardian",
    email = null,
    url = url("https://github.com/guardian")
  )
)

val awsSdkVersion = "1.12.676"
val awscalaVersion = "0.9.2"
val circeVersion = "0.13.0"
val commonDependencies = Seq(
  "org.typelevel" %% "cats-core" % "2.10.0",
  "joda-time" % "joda-time" % "2.12.5",
  "org.joda" % "joda-convert" % "2.2.3",
  "com.github.seratch" %% "awscala-iam" % awscalaVersion,
  "com.github.seratch" %% "awscala-sts" % awscalaVersion,
  "com.github.seratch" %% "awscala-dynamodb" % awscalaVersion,
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "org.scalacheck" %% "scalacheck" % "1.17.0" % Test,
  "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.13"
)
lazy val commonSettings = Seq(
  scalaVersion := "2.13.12",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-release:8",
    "-Xfatal-warnings"
  ),
  Test / testOptions ++= Seq(
    Tests.Argument(TestFrameworks.ScalaTest, "-o"),
    Tests.Argument(TestFrameworks.ScalaTest, "-u", "logs/test-reports")
  )
)

/*
Workaround for CVE-2020-36518 in Jackson
@see https://github.com/orgs/playframework/discussions/11222
 */
val jacksonVersion = "2.15.3"
val jacksonDatabindVersion = "2.15.3"

val jacksonOverrides = Seq(
  "com.fasterxml.jackson.core" % "jackson-core",
  "com.fasterxml.jackson.core" % "jackson-annotations",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"
).map(_ % jacksonVersion)

val jacksonDatabindOverrides = Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion
)

val pekkoSerializationJacksonOverrides = Seq(
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor",
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names",
  "com.fasterxml.jackson.module" %% "jackson-module-scala"
).map(_ % jacksonVersion)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, JDebPackaging, SystemdPlugin)
  .dependsOn(configTools % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := """janus""",
    version := "1.0-SNAPSHOT", // must match URL in cloudformation userdata
    Universal / javaOptions ++= Seq(
      "-Dconfig.file=/etc/gu/janus.conf", // for PROD, overridden by local sbt file
      "-Dpidfile.path=/dev/null",
      "-J-Xms1g",
      "-J-Xmx1g"
    ),
    libraryDependencies ++= commonDependencies ++ Seq(
      ws,
      filters,
      "com.gu.play-googleauth" %% "play-v30" % "4.0.0",
      "com.amazonaws" % "aws-java-sdk-iam" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
      "net.logstash.logback" % "logstash-logback-encoder" % "7.3" // scala-steward:off
    ) ++ jacksonDatabindOverrides
      ++ jacksonOverrides
      ++ pekkoSerializationJacksonOverrides,
    dependencyOverrides += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2", // Avoid binary incompatibility error.

    // local development
    playDefaultPort := 9100,
    Test / fork := false,

    // packaging / running package
    Assets / pipelineStages := Seq(digest),
    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false,
    Debian / topLevelDirectory := Some(normalizedName.value),
    Debian / serverLoading := Some(Systemd),
    Debian / maintainer := "Developer Experience <dig.dev.tooling@theguardian.com>",
    Debian / packageSummary := "Janus webapp",
    Debian / packageDescription := "Janus: Google-based federated AWS login"
  )

lazy val configTools = (project in file("configTools"))
  .enablePlugins(SbtTwirl)
  .settings(
    addCompilerPlugin(
      "org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full
    ),
    commonSettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.typesafe" % "config" % "1.4.0",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-config" % "0.8.0",
      "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion
    ) ++ jacksonDatabindOverrides,
    name := "janus-config-tools",
    description := "Library for reading and writing Janus configuration files",
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    publishTo := sonatypePublishTo.value,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setNextVersion,
      commitNextVersion
      /** Branch protection on the remote repository does not allow pushChanges
        * to succeed therefore the step below is disabled. All other release
        * steps are the same as the default release process.
        */
//      pushChanges
    ),
    releaseProcess += releaseStepCommandAndRemaining("sonatypeRelease")
  )

import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd
import play.sbt.PlayImport.PlayKeys.*
import sbt.Keys.*
import sbt.{addCompilerPlugin, *}
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations.*
import sbtversionpolicy.withsbtrelease.ReleaseVersion

ThisBuild / organization := "com.gu"
ThisBuild / licenses := Seq(License.Apache2)

val awscalaVersion = "0.9.2"
val circeVersion = "0.14.10"
val commonDependencies = Seq(
  "org.typelevel" %% "cats-core" % "2.12.0",
  "joda-time" % "joda-time" % "2.13.0",
  "org.joda" % "joda-convert" % "3.0.1",
  "com.github.seratch" %% "awscala-iam" % awscalaVersion,
  "com.github.seratch" %% "awscala-sts" % awscalaVersion,
  "com.github.seratch" %% "awscala-dynamodb" % awscalaVersion,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.scalacheck" %% "scalacheck" % "1.18.1" % Test,
  "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test,
  "ch.qos.logback" % "logback-classic" % "1.5.16"
)
lazy val commonSettings = Seq(
  scalaVersion := "2.13.15",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-release:11",
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
val jacksonVersion = "2.18.2"
val jacksonDatabindVersion = "2.18.2"

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
  .aggregate(configTools)
  .settings(
    commonSettings,
    name := """janus""",
    // The version is concatenated with the name to generate the filename for the deb file when building this project.
    // The result must match the URL in the cloudformation userdata in another repository, so the version is hard-coded.
    // We hard-code it only in the Debian scope so it affects the name of the deb file, but does not override the version
    // that is used for the published config-tools library.
    Debian / version := "1.0-SNAPSHOT",
    Universal / javaOptions ++= Seq(
      "-Dconfig.file=/etc/gu/janus.conf", // for PROD, overridden by local sbt file
      "-Dpidfile.path=/dev/null",
      "-J-Xms1g",
      "-J-Xmx1g"
    ),
    libraryDependencies ++= commonDependencies ++ Seq(
      ws,
      filters,
      "com.gu.play-googleauth" %% "play-v30" % "19.0.0",
      "net.logstash.logback" % "logstash-logback-encoder" % "7.3" // scala-steward:off
    ) ++ jacksonDatabindOverrides
      ++ jacksonOverrides
      ++ pekkoSerializationJacksonOverrides,
    dependencyOverrides += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2", // Avoid binary incompatibility error.

    // local development
    playDefaultPort := 9100,
    Test / fork := false,

    // Settings for sbt release, which gets run from the root in the reusable workflow,
    // the configTools module (below) is the one that will be published.
    // We skip publishing here, because we do not want to publish the root module.
    publish / skip := true,
    releaseVersion := ReleaseVersion
      .fromAggregatedAssessedCompatibilityWithLatestRelease()
      .value,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      setNextVersion,
      commitNextVersion
    ),

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
      "org.typelevel" %% "kind-projector" % "0.13.3" cross CrossVersion.full
    ),
    commonSettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.typesafe" % "config" % "1.4.3",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-config" % "0.10.1"
    ) ++ jacksonDatabindOverrides,
    name := "janus-config-tools",
    description := "Library for reading and writing Janus configuration files"
  )

import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd
import play.sbt.PlayImport.PlayKeys.*
import sbt.*
import sbt.Keys.*
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations.*
import sbtversionpolicy.withsbtrelease.ReleaseVersion

ThisBuild / organization := "com.gu"
ThisBuild / licenses := Seq(License.Apache2)

val awsSdkVersion = "2.32.21"
val circeVersion = "0.14.14"
val commonDependencies = Seq(
  "org.typelevel" %% "cats-core" % "2.13.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.scalacheck" %% "scalacheck" % "1.18.1" % Test,
  "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test,
  "ch.qos.logback" % "logback-classic" % "1.5.18"
)
lazy val commonSettings = Seq(
  scalaVersion := "3.3.6",
  scalacOptions ++= Seq(
    "-feature",
    "-release:11"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 13)) => Seq("-Xfatal-warnings")
    case Some((3, _))  => Seq("-Werror")
    case _             => Seq.empty
  }),
  Test / testOptions ++= Seq(
    Tests.Argument(TestFrameworks.ScalaTest, "-o"),
    Tests.Argument(TestFrameworks.ScalaTest, "-u", "logs/test-reports")
  )
)

/*
 * To decide when to remove any dependency from this list:
 * 1. Comment out the dependency
 * 2. Run 'sbt Runtime/dependencyList'
 * If an earlier version of the dependency does NOT appear in the output it's safe to remove from this list.
 */
val safeTransitiveDeps = Seq(
  "io.netty" % "netty-codec-http2" % "4.1.124.Final" % Runtime
)

lazy val root: Project = (project in file("."))
  .enablePlugins(PlayScala, JDebPackaging, SystemdPlugin)
  .dependsOn(configTools % "compile->compile;test->test")
  .aggregate(configTools)
  .settings(
    commonSettings,
    name := "janus",
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
    // allows us to kick off the frontend dev-server when the API is run
    playRunHooks ++= Seq(
      RunClientHook(root.base),
      DockerComposeHook(root.base)
    ),
    libraryDependencies ++= commonDependencies ++ Seq(
      ws,
      filters,
      "com.gu.play-googleauth" %% "play-v30" % "25.2.1",
      "com.gu.play-secret-rotation" %% "play-v30" % "14.5.2",
      "com.gu.play-secret-rotation" %% "aws-parameterstore-sdk-v2" % "14.5.2",
      "software.amazon.awssdk" % "iam" % awsSdkVersion,
      "software.amazon.awssdk" % "sts" % awsSdkVersion,
      "software.amazon.awssdk" % "dynamodb" % awsSdkVersion,
      "net.logstash.logback" % "logstash-logback-encoder" % "8.1", // scala-steward:off
      "com.webauthn4j" % "webauthn4j-core" % "0.29.5.RELEASE",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test
    ) ++ safeTransitiveDeps,

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
    releaseCrossBuild := true,
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
    commonSettings,
    crossScalaVersions := Seq(
      "2.13.16",
      scalaVersion.value
    ),
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.typesafe" % "config" % "1.4.4",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-config" % "0.10.2"
    ),
    name := "janus-config-tools",
    description := "Library for reading and writing Janus configuration files"
  )

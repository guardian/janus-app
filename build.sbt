import com.gu.riffraff.artifact.RiffRaffArtifact
import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._
import play.sbt.PlayImport.PlayKeys._
import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd
import sbt.Keys._
import sbt.{addCompilerPlugin, _}


val awsSdkVersion = "1.11.663"
val awscalaVersion = "0.8.1"
val circeVersion = "0.12.3"
val commonDependencies = Seq(
  "org.typelevel" %% "cats-core" % "2.0.0",
  "joda-time" % "joda-time" % "2.10.5",
  "org.joda" % "joda-convert" % "2.2.1",
  "com.github.seratch" %% "awscala-iam" % awscalaVersion,
  "com.github.seratch" %% "awscala-sts" % awscalaVersion,
  "com.github.seratch" %% "awscala-dynamodb" % awscalaVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
)
lazy val commonSettings = Seq(
  scalaVersion := "2.12.11",
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-target:jvm-1.8", "-Xfatal-warnings"),
  testOptions in Test ++= Seq(Tests.Argument(TestFrameworks.ScalaTest, "-o"), Tests.Argument(TestFrameworks.ScalaTest, "-u", "logs/test-reports"))
)


lazy val root = (project in file("."))
  .enablePlugins(PlayScala, RiffRaffArtifact, JDebPackaging, SystemdPlugin)
  .dependsOn(configTools % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := """janus""",
    version := "1.0-SNAPSHOT", // must match URL in cloudformation userdata
    javaOptions in Universal ++= Seq(
      "-Dconfig.file=/etc/gu/janus.conf", // for PROD, overridden by local sbt file
      "-Dpidfile.path=/dev/null",
      "-J-XX:MaxRAMFraction=2",
      "-J-XX:InitialRAMFraction=2",
      "-J-XX:+UseG1GC",
      "-J-XX:G1HeapRegionSize=32m",
      "-J-XX:+PrintGCDetails",
      "-J-XX:+PrintGCDateStamps",
      "-J-Xloggc:/var/log/${packageName.value}/gc.log",
      "-J-XX:+UseCompressedOops",
      "-J-XX:+UseStringDeduplication"
    ),

    libraryDependencies ++= commonDependencies ++ Seq(
      ws,
      filters,
      "com.gu" %% "play-googleauth" % "0.7.4",
      "com.amazonaws" % "aws-java-sdk-iam" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion
    ),

    // local development
    playDefaultPort := 9100,
    fork in Test := false,

    // deployment
    riffRaffPackageType := (packageBin in Debian).value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffArtifactResources += (file("cloudformation/janus.template.yaml"), s"${name.value}-cfn/cfn.yaml"),

    // packaging / running package
    pipelineStages in Assets := Seq(digest),
    sources in (Compile,doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false,

    topLevelDirectory in Debian := Some(normalizedName.value),
    serverLoading in Debian := Some(Systemd),
    debianPackageDependencies := Seq("java8-runtime-headless"),
    maintainer in Debian := "Developer Experience <dig.dev.tooling@theguardian.com>",
    packageSummary in Debian := "Janus webapp",
    packageDescription in Debian := "Janus: Google-based federated AWS login",
  )

lazy val configTools = (project in file("configTools"))
  .enablePlugins(SbtTwirl)
  .settings(
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8"),
    commonSettings,
    version := "0.1.0-SNAPSHOT",
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.typesafe" % "config" % "1.4.0",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-config" % "0.7.0"
    )
  )

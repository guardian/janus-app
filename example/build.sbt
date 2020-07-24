ThisBuild / scalaVersion     := "2.12.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "com/example"

lazy val root = (project in file("."))
  .settings(
    name := "com/example",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.1.1" % Test,
      "com.gu" %% "janus-config-tools" % "0.0.3"
    )
  )

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "com.example"

lazy val root = (project in file("."))
  .settings(
    name := "example",
    libraryDependencies ++= Seq(
      "com.gu" %% "janus-config-tools" % "5.0.0",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )

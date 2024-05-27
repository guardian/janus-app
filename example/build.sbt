ThisBuild / scalaVersion := "2.13.14"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "com.example"

lazy val root = (project in file("."))
  .settings(
    name := "example",
    libraryDependencies ++= Seq(
      "com.gu" %% "janus-config-tools" % "0.0.5",
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
    )
  )

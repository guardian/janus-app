ThisBuild / scalaVersion := "2.13.16"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "com.example"

lazy val root = (project in file("."))
  .settings(
    name := "example",
    libraryDependencies ++= Seq(
      "com.gu" %% "janus-config-tools" % "3.0.0",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )

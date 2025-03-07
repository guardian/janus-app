resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin(
  "com.github.sbt" % "sbt-native-packager" % "1.11.1"
)

// The Play plugin
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.6")

addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.1.0")

libraryDependencies += "org.vafer" % "jdeb" % "1.13" artifacts Artifact(
  "jdeb",
  "jar",
  "jar"
)

// These are for releasing to Sonatype
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.4.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.12.2")
addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "3.2.1")

addDependencyTreePlugin

// Formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")

// Scala 3 upgrade
addSbtPlugin("ch.epfl.scala" % "sbt-scala3-migrate" % "0.6.2")

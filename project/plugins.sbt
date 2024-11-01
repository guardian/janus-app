resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// sbt-native-packager cannot be updated to >1.10.4 until Play supports scala-xml 2
addSbtPlugin(
  "com.github.sbt" % "sbt-native-packager" % "1.10.4"
) // scala-steward:off

// The Play plugin
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.5")

addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.0.0")

libraryDependencies += "org.vafer" % "jdeb" % "1.11" artifacts Artifact(
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
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

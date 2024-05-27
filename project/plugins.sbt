resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// sbt-native-packager cannot be updated to >1.9.9 until Play supports scala-xml 2
addSbtPlugin(
  "com.github.sbt" % "sbt-native-packager" % "1.10.0"
) // scala-steward:off

// The Play plugin
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.1")

addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.0.0")

libraryDependencies += "org.vafer" % "jdeb" % "1.10" artifacts Artifact(
  "jdeb",
  "jar",
  "jar"
)

// These are for releasing to Sonatype
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.4.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.10.0")
addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "3.2.1")

addDependencyTreePlugin

// Formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

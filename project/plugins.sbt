resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// sbt-native-packager cannot be updated to >1.9.9 until Play supports scala-xml 2
addSbtPlugin(
  "com.github.sbt" % "sbt-native-packager" % "1.9.16"
) // scala-steward:off

// The Play plugin
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")

libraryDependencies += "org.vafer" % "jdeb" % "1.10" artifacts Artifact(
  "jdeb",
  "jar",
  "jar"
)

// These are for releasing to Sonatype
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.21")
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")

addDependencyTreePlugin

// Formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

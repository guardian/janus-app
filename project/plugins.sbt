resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin(
  "com.github.sbt" % "sbt-native-packager" % "1.11.7"
)

// The Play plugin
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.10")

addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.1.0")

libraryDependencies += "org.vafer" % "jdeb" % "1.14" artifacts Artifact(
  "jdeb",
  "jar",
  "jar"
)

// These are for releasing to Sonatype
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.4.0")
addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "3.2.1")

addDependencyTreePlugin

// Formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")

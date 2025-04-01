val Http4sVersion = "0.23.30"
val CirceVersion = "0.14.10"
val MunitVersion = "1.1.0"
val LogbackVersion = "1.5.16"
val MunitCatsEffectVersion = "2.0.0"

lazy val server = (project in file("."))
  .settings(
    organization := "com.example",
    name := "Server",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.15",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % Http4sVersion,
      "org.http4s" %% "http4s-dsl"          % Http4sVersion,
      "org.typelevel" %% "cats-effect"       % "3.5.2",
      "org.http4s" %% "http4s-blaze-server" % "0.23.9",
      "ch.qos.logback" % "logback-classic" % "1.4.11",
      "org.slf4j" % "slf4j-api" % "2.0.7",
      "org.http4s" %% "http4s-dsl" % "0.23.16",
      "org.http4s" %% "http4s-ember-server" % "0.23.16",
    )
  )

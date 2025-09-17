val Http4sVersion = "0.23.30"
val CirceVersion = "0.14.13"
val MunitVersion = "1.1.0"
val LogbackVersion = "1.5.16"
val MunitCatsEffectVersion = "2.0.0"

lazy val server = (project in file("."))
  .settings(
    organization := "com.example",
    name := "Server",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "3.6.3",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % Http4sVersion,
      "org.http4s" %% "http4s-dsl"          % Http4sVersion,
      "org.typelevel" %% "cats-effect"       % "3.6.1",
    )
  )

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.11"

lazy val root = (project in file("."))
  .settings(
    name := "ProjectStreamsTweedeZit"
  )

val AkkaVersion = "2.8.3"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion

libraryDependencies ++= Seq(
  // Akka Libraries
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",

  // Requesting and processing data
  "com.lihaoyi" %% "requests" % "0.8.0",
  "com.lihaoyi" %% "ujson" % "3.0.0",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "2.2.0",
  "com.typesafe.play" %% "play-json" % "2.9.4"
)



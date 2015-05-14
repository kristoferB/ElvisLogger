name := """Elvis-log"""

version := "1.0"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.9",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test")

libraryDependencies ++= Seq(
"net.databinder.dispatch" %% "dispatch-core" % "0.11.1",
"org.scala-lang.modules" %% "scala-xml" % "1.0.2",
"org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1",
  "org.json4s" %% "json4s-native" % "3.2.11",
  "org.json4s" %% "json4s-ext" % "3.2.11",
  "com.chuusai" %% "shapeless" % "2.1.0")

libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "1.8.0"

import ScalaxbKeys._

organization := "sekvensa.elvis"

scalaxbSettings

packageName in (Compile, scalaxb) := "sekvensa.elvis"

dispatchVersion in (Compile, scalaxb) := "0.11.1"

async in (Compile, scalaxb) := true

sourceGenerators in Compile <+= scalaxb in Compile

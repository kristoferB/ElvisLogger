name := """Elvis-log"""

version := "1.1"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  //these were previously found in Core-Assembly-jar
  //"org.apache.activemq" % "activemq-camel" % "5.13.0",
  "org.apache.activemq" % "activemq-camel" % "5.11.1",
  "org.apache.camel" % "camel-core" % "2.14.1",
  "com.typesafe.akka" % "akka-camel_2.11" % "2.4.1",
  "org.slf4j" % "slf4j-simple" % "1.7.7",

  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.1",
  "com.typesafe.akka" %% "akka-persistence" % "2.4.1",
  "org.iq80.leveldb"            % "leveldb"          % "0.7",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test")

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.1",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1",
  "org.json4s" %% "json4s-native" % "3.2.11",
  "org.json4s" %% "json4s-ext" % "3.2.11",
  "com.chuusai" %% "shapeless" % "2.1.0")

libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.0.0"

//scalaxb dependencies
import ScalaxbKeys._
organization := "sekvensa.elvis"
scalaxbSettings
packageName in (Compile, scalaxb) := "sekvensa.elvis"
dispatchVersion in (Compile, scalaxb) := "0.11.1"
async in (Compile, scalaxb) := true
sourceGenerators in Compile <+= scalaxb in Compile


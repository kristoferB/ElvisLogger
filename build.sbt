name := """Elvis-log"""

version := "1.1"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  //these were previously found in Core-Assembly-jar
  "org.apache.activemq" % "activemq-camel" % "5.13.0",
  "org.apache.camel" % "camel-core" % "2.16.1",
  "com.typesafe.akka" % "akka-camel_2.11" % "2.4.1",
  "org.slf4j" % "slf4j-api" % "1.7.14",
  "org.slf4j" % "slf4j-log4j12" % "1.7.14",

  
  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.1",
  "com.typesafe.akka" %% "akka-persistence" % "2.4.1",
  "org.iq80.leveldb"            % "leveldb"          % "0.7",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test")

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.3",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
  "org.json4s" %% "json4s-native" % "3.3.0",
  "org.json4s" %% "json4s-ext" % "3.3.0",
  "com.chuusai" %% "shapeless" % "2.2.5")

libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.8.0"

import ScalaxbKeys._

organization := "sekvensa.elvis"

scalaxbSettings

packageName in (Compile, scalaxb) := "sekvensa.elvis"

dispatchVersion in (Compile, scalaxb) := "0.11.1"

async in (Compile, scalaxb) := true

sourceGenerators in Compile <+= scalaxb in Compile


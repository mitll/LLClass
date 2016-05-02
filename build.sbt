name := "LLClass"

organization := "edu.mit.ll"

version := "1.0"

scalaVersion := "2.11.8"

sbtVersion := "0.13.11"

test in assembly := {}

// get the assembly plugin, not using https
resolvers += "scalasbt" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases"

//resolvers += Resolver.sonatypeRepo("snapshots")

mainClass := Some("mitll.lid.LLClass")

libraryDependencies += "org.apache.commons" % "commons-email" % "1.3.1"

libraryDependencies += "commons-io" % "commons-io" % "2.4"

libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.1"

// TODO : update to run with 2.11 scala to use latest mallet
//libraryDependencies += "cc.mallet" % "mallet" % "2.0.7-RC2"

libraryDependencies += "tw.edu.ntu.csie" % "libsvm" % "3.1"

libraryDependencies += "org.avaje.ebeanorm" % "avaje-ebeanorm-agent" % "3.1.1"

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

libraryDependencies += "ch.qos.logback" %  "logback-classic" % "1.1.7"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"

libraryDependencies +=  "com.lihaoyi" %% "sourcecode" % "0.1.1" // Scala-JVM

libraryDependencies +=  "org.scalaj" %% "scalaj-http" % "2.3.0"

lazy val http4sVersion = "0.13.2"

val circeVersion = "0.4.1"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion
)

mainClass in assembly := Some("mitll.lid.LLClass")



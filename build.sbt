name := "LLClass"

organization := "edu.mit.ll"

version := "1.0"

scalaVersion := "2.11.8"

sbtVersion := "0.13.11"

// get the assembly plugin, not using https
resolvers += "scalasbt" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases"

mainClass := Some("mitll.LLClass")

libraryDependencies += "org.apache.commons" % "commons-email" % "1.3.1"

libraryDependencies += "commons-io" % "commons-io" % "2.4"

libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.1"

// TODO : update to run with 2.11 scala to use latest mallet
//libraryDependencies += "cc.mallet" % "mallet" % "2.0.7-RC2"

libraryDependencies += "tw.edu.ntu.csie" % "libsvm" % "3.1"

libraryDependencies += "org.avaje.ebeanorm" % "avaje-ebeanorm-agent" % "3.1.1"

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"

mainClass in assembly := Some("mitll.LLClass")

libraryDependencies ++= Seq(

)

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

libraryDependencies += "ch.qos.logback" %  "logback-classic" % "1.1.7"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"

libraryDependencies +=  "com.lihaoyi" %% "sourcecode" % "0.1.1" // Scala-JVM



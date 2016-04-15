name := "MITLL_LID"

organization := "edu.mit.ll"

version := "1.0"

scalaVersion := "2.11.8"

sbtVersion := "0.13.11"

// get the assembly plugin, not using https
resolvers += "scalasbt" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases"

mainClass := Some("mitll.MITLL_LID")

scalaSource in Compile := baseDirectory.value / "src"

libraryDependencies += "org.apache.commons" % "commons-email" % "1.3.1"

libraryDependencies += "commons-io" % "commons-io" % "2.4"

libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.1"

// TODO : update to run with 2.11 scala to use latest mallet
//libraryDependencies += "cc.mallet" % "mallet" % "2.0.7-RC2"

libraryDependencies += "tw.edu.ntu.csie" % "libsvm" % "3.1"

libraryDependencies += "org.avaje.ebeanorm" % "avaje-ebeanorm-agent" % "3.1.1"

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"

mainClass in assembly := Some("mitll.MITLL_LID")

libraryDependencies ++= Seq(
)

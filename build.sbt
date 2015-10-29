name := "MITLL_LID"

version := "1.0"

scalaVersion := "2.10.3"

resolvers += "scalasbt" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases"////oss.sonatype.org/content/repositories/snapshots"

mainClass := Some("mitll.MITLL_LID")

scalaSource in Compile := baseDirectory.value / "src"

libraryDependencies += "org.apache.commons" % "commons-email" % "1.3.1"

libraryDependencies += "commons-io" % "commons-io" % "2.4"

libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.1"

//libraryDependencies += "cc.mallet" % "mallet" % "2.0.7-RC2"

libraryDependencies += "tw.edu.ntu.csie" % "libsvm" % "3.1"

libraryDependencies += "org.avaje.ebeanorm" % "avaje-ebeanorm-agent" % "3.1.1"

mainClass in assembly := Some("mitll.MITLL_LID")

libraryDependencies ++= Seq(
)

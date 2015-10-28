name := "MITLL_LID"

version := "1.0"

scalaVersion := "2.11.7"

mainClass := Some("mitll.MITLL_LID")

scalaSource in Compile := baseDirectory.value / "src"

libraryDependencies += "org.apache.commons" % "commons-email" % "1.3.1"

libraryDependencies += "commons-io" % "commons-io" % "2.4"

libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.1"

libraryDependencies += "cc.mallet" % "mallet" % "2.0.7-RC2"

libraryDependencies += "tw.edu.ntu.csie" % "libsvm" % "3.1"

libraryDependencies += "org.avaje.ebeanorm" % "avaje-ebeanorm-agent" % "3.1.1"

libraryDependencies ++= Seq(
)

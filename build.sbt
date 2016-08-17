name := "finagle-mysql-example"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.twitter" %% "finagle-mysql" % "6.36.0",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "com.wix" % "wix-embedded-mysql" % "2.0.1" % "test"
)
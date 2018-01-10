name := "bundle-pdfs"
organization := "com.stellmangreene"
version := "1.0"
scalaVersion := "2.12.3"
scalacOptions ++= Seq("-feature")

libraryDependencies ++= Seq(
   "org.apache.commons" % "commons-text" % "1.2",
   "com.github.pathikrit" %% "better-files" % "3.4.0",
   "com.typesafe" % "config" % "1.3.2",

   // Log dependencies
   "org.slf4j" % "slf4j-api" % "1.7.25",
   "org.slf4j" % "slf4j-simple" % "1.7.25",
   "org.log4s" %% "log4s" % "1.3.5",

   // Test dependencies
   "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

// sbt-eclipse settings
EclipseKeys.withSource := true


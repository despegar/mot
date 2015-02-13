organization := "mot"

name := "mot"

version := "0.8-RC5"

scalaVersion := "2.11.4"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-optimize")

//publishTo := Some("nexus-snapshots" at "http://nexus.despegar.it:8080/nexus/content/repositories/snapshots/")
publishTo := Some("nexus-snapshots" at "http://nexus.despegar.it:8080/nexus/content/repositories/releases/")
//publishTo := Some("nexus-snapshots" at "http://nexus:8080/nexus/content/repositories/releases-miami/")
//publishTo := Some("nexus-snapshots" at "http://nexus:8080/nexus/content/repositories/snapshots-miami/")

libraryDependencies ++=
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2" ::
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2" ::
  "ch.qos.logback" % "logback-classic" % "1.1.2" % Test ::
  "io.netty" % "netty-common" % "4.0.23.Final" ::
  "org.scalatest" %% "scalatest" % "2.2.1" % "test" ::
  "org.hdrhistogram" % "HdrHistogram" % "2.1.3" % "test" ::
  Nil
  
// Do not include src/{main,test}/java in the configuration, to avoid having sbt-eclipse generate them empty

unmanagedSourceDirectories in Compile := (scalaSource in Compile).value :: Nil

unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil
  
fork := true
	
connectInput := true

javaOptions  ++= Seq("-Xmx6200m", "-Xms6200m", "-XX:NewSize=5000m")

outputStrategy := Some(StdoutOutput)

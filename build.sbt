organization := "mot"

name := "mot"

version := "0.6-SNAPSHOT"

scalaVersion := "2.11.4"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

//publishTo := Some("nexus-snapshots" at "http://nexus.despegar.it:8080/nexus/content/repositories/snapshots/")
publishTo := Some("nexus-snapshots" at "http://nexus:8080/nexus/content/repositories/snapshots-miami/")

libraryDependencies ++=
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2" ::
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2" ::
  "ch.qos.logback" % "logback-classic" % "1.1.2" % Test ::
  "io.netty" % "netty-common" % "4.0.23.Final" ::
  Nil
  
// Do not include src/{main,test}/java in the configuration, to avoid having sbt-eclipse generate them empty

unmanagedSourceDirectories in Compile := (scalaSource in Compile).value :: Nil

unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil
  
fork := true
	
connectInput := true

javaOptions  ++= Seq("-Xmx2500m", "-Xms2500m", "-XX:NewSize=2000m")

outputStrategy := Some(StdoutOutput)

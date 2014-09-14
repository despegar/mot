organization := "mot"

name := "mot"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.4"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

//publishTo := Some("nexus-snapshots" at "http://nexus.despegar.it:8080/nexus/content/repositories/snapshots/")
publishTo := Some("nexus-snapshots" at "http://nexus:8080/nexus/content/repositories/snapshots-miami/")

libraryDependencies ++=
  "com.typesafe" %% "scalalogging-slf4j" % "1.0.1" ::
  "ch.qos.logback" % "logback-classic" % "1.0.13" % Test ::
  Nil
  
// Do not include src/{main,test}/java in the configuration, to avoid having sbt-eclipse generate them empty

unmanagedSourceDirectories in Compile := (scalaSource in Compile).value :: Nil

unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil
  
fork := true

connectInput := true

javaOptions  ++= Seq("-Xmx1900m", "-Xms1900m", "-XX:NewSize=1800m")

outputStrategy := Some(StdoutOutput)

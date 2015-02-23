organization := "mot"

name := "mot"

version := "0.8-SNAPSHOT"

scalaVersion := "2.11.5"

scalacOptions := Seq(
	"-feature", 
	"-deprecation", 
	"-optimize",
	"-unchecked",
	"-language:postfixOps", 
	"-language:reflectiveCalls", 
	"-language:implicitConversions", 
	"-Ywarn-dead-code",
	"-Ywarn-inaccessible",
	"-Ywarn-nullary-unit",
	"-Ywarn-nullary-override",
	"-Ywarn-infer-any")
	
crossScalaVersions := Seq("2.10.4", "2.11.5")

//publishTo := Some("bsas-snapshots" at "http://nexus.despegar.it:8080/nexus/content/repositories/snapshots/")
//publishTo := Some("bsas-releases" at "http://nexus.despegar.it:8080/nexus/content/repositories/releases/")
//publishTo := Some("miami-releases" at "http://nexus:8080/nexus/content/repositories/releases-miami/")
//publishTo := Some("miami-snapshots" at "http://nexus:8080/nexus/content/repositories/snapshots-miami/")

libraryDependencies ++=
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2" ::
  "ch.qos.logback" % "logback-classic" % "1.1.2" % Test ::
  "io.netty" % "netty-common" % "4.0.25.Final" ::
  "org.scalatest" %% "scalatest" % "2.2.4" % "test" ::
  "org.hdrhistogram" % "HdrHistogram" % "2.1.4" % "test" ::
  Nil
  
libraryDependencies := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, major)) if major >= 11 =>
      libraryDependencies.value ++ Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3")
    case _ =>
      libraryDependencies.value
  }
}

// Do not include src/{main,test}/java in the configuration, to avoid having sbt-eclipse generate them empty
unmanagedSourceDirectories in Compile := (scalaSource in Compile).value :: Nil
unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil
  
fork := true
	
connectInput := true

javaOptions  ++= Seq("-Xmx6200m", "-Xms6200m", "-XX:NewSize=5000m")

outputStrategy := Some(StdoutOutput)

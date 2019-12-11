name := "ChordSimulation"
version := "1.0"
organization := "jesseuic"
name := "chordsim"

scalaVersion := "2.11.12"

javacOptions ++= Seq("-source", "1.8")
javacOptions ++= Seq("-target", "1.8")
lazy val root = (project in file("."))
  .settings(
    name := "ChordSimulation",
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    libraryDependencies +="org.slf4j" % "slf4j-api" % "1.7.28",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
    libraryDependencies +="com.typesafe" % "config" % "1.3.2",
    libraryDependencies +="org.scalatest" %% "scalatest" % "3.0.5" % "test",
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.26",
    libraryDependencies += "com.typesafe.akka" %% "akka-http"   % "10.1.10",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.26",
    libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.10",
    libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.5.26" % Test,
    libraryDependencies += "com.typesafe.akka" %% "akka-http-testkit" % "10.1.10" % Test
  ).
  enablePlugins(AssemblyPlugin)
  enablePlugins(DockerPlugin)


assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

trapExit := false

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8-jre")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

imageNames in docker := Seq(
  // Sets the latest tag
  ImageName(s"${organization.value}/${name.value}:latest"),

  // Sets a name with a tag that contains the project version
  ImageName(
    namespace = Some(organization.value),
    repository = name.value,
    tag = Some("v" + version.value)
  )
)
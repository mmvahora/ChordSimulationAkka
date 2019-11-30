name := "CourseProject"

version := "1.0"

scalaVersion := "2.11.8"

javacOptions ++= Seq("-source", "1.8") 
javacOptions ++= Seq("-target", "1.8")
lazy val root = (project in file("."))
.settings(
    name := "ChordSimulation",
    libraryDependencies +="org.slf4j" % "slf4j-api" % "1.7.28",
    libraryDependencies +="com.typesafe" % "config" % "1.3.2",
    libraryDependencies +="org.scalatest" %% "scalatest" % "3.0.5" % "test"

  ).
  enablePlugins(AssemblyPlugin)


assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
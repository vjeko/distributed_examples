name := "Hello"
 
version := "1.0"
 
scalaVersion := "2.11.2"
 
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
 
libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor_2.11" % "2.3.6",
  "com.typesafe.akka" % "akka-cluster_2.11" % "2.3.6"
)


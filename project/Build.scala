package sample

import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtAspectj.{ Aspectj, aspectjSettings, useInstrumentedClasses }
import com.typesafe.sbt.SbtAspectj.AspectjKeys.inputs
import com.typesafe.sbteclipse.plugin.EclipsePlugin._

object ConcurrencyeBuild extends Build {
  lazy val concurrency = Project(
    id = "concurrency",
    base = file("."),
    settings = Defaults.defaultSettings ++ aspectjSettings ++ Seq(
      EclipseKeys.withSource := true,
      organization := "com.typesafe.sbt.aspectj",
      version := "0.1",
      scalaVersion := "2.11.4",

      libraryDependencies += "org.spire-math" %% "spire" % "0.9.0",
      libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.6",
      libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.3.6",
      libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.7",
      libraryDependencies += "org.slf4j" % "log4j-over-slf4j" % "1.7.7",
      libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2",
      libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3",
      libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      libraryDependencies += "com.assembla.scala-incubator" %% "graph-core" % "1.9.0",
      libraryDependencies += "com.assembla.scala-incubator" %% "graph-dot" % "1.9.0",

      // add akka-actor as an aspectj input (find it in the update report)
      inputs in Aspectj <++= update map { report =>
        report.matching(moduleFilter(organization = "com.typesafe.akka", name = "akka-actor*"))
      },

      // replace the original akka-actor jar with the instrumented classes in runtime
      fullClasspath in Runtime <<= useInstrumentedClasses(Runtime)
    )
  )
}

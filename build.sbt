import sbt.internal.ProjectMatrix
import sbtghactions.JavaSpec
import complete.DefaultParsers._
import sbt.Reference.display
import org.typelevel.sbt.tpolecat.{CiMode, DevMode}

val munitVersion = "1.1.1"
val munitScalacheckVersion = "1.1.0"
val catsVersion = "2.13.0"
val scalatestVersion = "3.2.19"
val weaverVersion = "0.9.2"

val isScala3 = Def.setting {
  // doesn't work well with >= 3.0.0 for `3.0.0-M1`
  scalaVersion.value.startsWith("3")
}

val mainScalaVersion = Build.Scala213
val jvmScalaVersions = Seq(Build.Scala213, Build.Scala3)

inThisBuild(
  List(
    scalaVersion := mainScalaVersion,
    organization := "com.github.jatcwang",
    homepage := Some(url("https://github.com/jatcwang/difflicious")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jatcwang",
        "Jacob Wang",
        "jatcwang@gmail.com",
        url("https://almostfunctional.com"),
      ),
    ),
    commands ++= Build.createBuildCommands(allModules),
    tpolecatDefaultOptionsMode := (if (sys.env.contains("CI")) CiMode else DevMode),
  ),
)

lazy val allModules = Seq(core, coretest, munit, scalatest, weaver, cats, benchmarks, docs).flatMap(_.projectRefs)

lazy val difflicious = Project("difflicious", file("."))
  .aggregate(allModules: _*)
  .settings(commonSettings, noPublishSettings)

lazy val core = projectMatrix
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(
    name := "difflicious-core",
    libraryDependencies ++= Seq(
      "dev.zio" %% "izumi-reflect" % "3.0.5",
      "com.lihaoyi" %% "fansi" % "0.5.0",
    ) ++ (if (isScala3.value) {
            Seq("com.softwaremill.magnolia1_3" %% "magnolia" % "1.3.18")
          } else
            Seq(
              "com.softwaremill.magnolia1_2" %% "magnolia" % "1.1.10",
              "org.scala-lang" % "scala-reflect" % Build.Scala213,
            )),
    Compile / sourceGenerators += Def.task {
      val file = (Compile / sourceManaged).value / "difflicious" / "TupleDifferInstances.scala"
      IO.write(file, TupleDifferInstancesGen.fileContent)
      Seq(file)
    }.taskValue,
  )
  .jvmPlatform(jvmScalaVersions)

lazy val munit = projectMatrix
  .in(file("modules/munit"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "difflicious-munit",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion,
    ),
  )
  .jvmPlatform(jvmScalaVersions)

lazy val scalatest = projectMatrix
  .in(file("modules/scalatest"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "difflicious-scalatest",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest-core" % scalatestVersion,
    ),
  )
  .jvmPlatform(jvmScalaVersions)

lazy val weaver = projectMatrix
  .in(file("modules/weaver"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "difflicious-weaver",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "weaver-core" % weaverVersion,
    ),
  )
  .jvmPlatform(jvmScalaVersions)

lazy val cats = projectMatrix
  .in(file("modules/cats"))
  .dependsOn(core, coretest % "test->test")
  .settings(commonSettings)
  .settings(
    name := "difflicious-cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
    ),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-laws" % catsVersion,
    ).map(_ % Test),
  )
  .jvmPlatform(jvmScalaVersions)

lazy val coretest = projectMatrix
  .in(file("modules/coretest"))
  .dependsOn(core)
  .settings(commonSettings, noPublishSettings)
  .settings(
    name := "coretest",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
    ),
    // Test deps
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion,
      "org.scalameta" %% "munit-scalacheck" % munitScalacheckVersion,
    ).map(_ % Test),
  )
  .jvmPlatform(jvmScalaVersions)

lazy val docs: ProjectMatrix = projectMatrix
  .dependsOn(core, coretest, cats, munit, scalatest, weaver)
  .enablePlugins(MdocPlugin)
  .settings(
    name := "docs",
    commonSettings,
    publish / skip := true,
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion,
      "org.typelevel" %% "weaver-cats" % weaverVersion,
    ),
  )
  .settings(
    mdocIn := file("docs/docs"),
    mdocExtraArguments ++= Seq("--noLinkHygiene"),
  )
  .settings(
    // Disable any2stringAdd deprecation in md files. Seems like mdoc macro generates code which
    // use implicit conversion to string
    scalacOptions ~= { opts =>
      val extraOpts =
        Seq(
          "-Wconf:msg=\".*method any2stringadd.*\":i",
          "-Wconf:msg=\".*The outer reference in this type test.*\":s", // This warning shows up if we use *final* case class in code blocks
          "-Wconf:msg=\".*method right in class Either.*\":s",
          "-Wconf:msg=\".*method get in class RightProjection.*\":s",
          "-Wconf:msg=\".*local (object|class).+?is never used\":s",
        )
      val removes = Set("-Wdead-code", "-Ywarn-dead-code", "-Wnonunit-statement") // we use ??? in various places
      (opts ++ extraOpts).filterNot(removes)
    },
  )
  .jvmPlatform(Seq(Build.Scala213))

lazy val benchmarks = projectMatrix
  .in(file("modules/benchmarks"))
  .dependsOn(coretest)
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .jvmPlatform(jvmScalaVersions)

lazy val commonSettings = Seq(
  versionScheme := Some("early-semver"),
  scalacOptions ++= (if (isScala3.value) Seq.empty[String] else Seq("-Wmacros:after")),
  // TODO: Not sure why but having these scalac options seems to crash the compiler complaining about
  // magnoalia class files having duplicate top level definitions..
  // Maybe related to https://github.com/scala/scala3/issues/18674
  scalacOptions --= (if (isScala3.value)
                       Seq(
                         "-Wunused:implicits",
                         "-Wunused:explicits",
                         "-Wunused:imports",
                         "-Wunused:locals",
                         "-Wunused:params",
                         "-Wunused:privates",
                       )
                     else Seq.empty),
  libraryDependencies ++= Seq(
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.3" cross CrossVersion.full),
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  ).filterNot(_ => isScala3.value),
)

lazy val noPublishSettings = Seq(
  publish / skip := true,
)

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches :=
  Seq(RefPredicate.StartsWith(Ref.Tag("v")))

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
    ),
  ),
)

ThisBuild / githubWorkflowBuildMatrixAdditions += ("scalaPlatform", List("jvm"))

ThisBuild / githubWorkflowScalaVersions := Seq("2_13", "3_0")

ThisBuild / githubWorkflowBuildSbtStepPreamble := Seq.empty

ThisBuild / githubWorkflowArtifactUpload := false

// Filter out MacOS and Windows cache steps to make yaml less noisy
ThisBuild / githubWorkflowGeneratedCacheSteps ~= { currentSteps =>
  currentSteps.filterNot(wf => wf.cond.exists(str => str.contains("macos") || str.contains("windows")))
}

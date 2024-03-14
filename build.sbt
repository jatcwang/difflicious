import sbt.internal.ProjectMatrix
import sbtghactions.JavaSpec
import complete.DefaultParsers._
import sbt.Reference.display

val munitVersion = "0.7.29"
val catsVersion = "2.9.0"
val scalatestVersion = "3.2.16"
val weaverVersion = "0.8.3"

val scala213 = "2.13.11"
val scala3 = "3.3.0"

val isScala3 = Def.setting {
  // doesn't work well with >= 3.0.0 for `3.0.0-M1`
  scalaVersion.value.startsWith("3")
}

val mainScalaVersion = scala213
val jvmScalaVersions = Seq(scala213, scala3)

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
  ),
)

lazy val allModules = List(core, coretest, munit, scalatest, weaver, cats, benchmarks, docs)

// taken from tapir: https://github.com/softwaremill/tapir/blob/08628a89a751f8bace95f1b57288da1f7c0c9dce/build.sbt#L279
val scopesDescription = "Scala version can be: 2.12, 2.13, 3; platform: JVM, JS, Native"
val compileScoped =
  inputKey[Unit](
    s"Compiles sources in the given scope. Usage: compileScoped [scala version] [platform]. $scopesDescription",
  )
val testScoped =
  inputKey[Unit](s"Run tests in the given scope. Usage: testScoped [scala version] [platform]. $scopesDescription")

val publishLocalScoped =
  inputKey[Unit](
    s"Publish artifacts to local ivy repository in the given scope. Usage: publishLocalScoped [scala version] [platform]. $scopesDescription",
  )

def filterProject(p: String => Boolean) =
  ScopeFilter(inProjects(allModules.flatMap(_.projectRefs).filter(pr => p(display(pr.project))): _*))

def filterByVersionAndPlatform(scalaVersionFilter: String, platformFilter: String) = filterProject { projectName =>
  val byPlatform =
    if (platformFilter == "JVM") !projectName.contains("JS") && !projectName.contains("Native")
    else projectName.contains(platformFilter)
  val byVersion = scalaVersionFilter match {
    case "2.13" => !projectName.contains("2_12") && !projectName.contains("3")
    case "2.12" => projectName.contains("2_12")
    case "3"    => projectName.contains("3")
  }

  byPlatform && byVersion
}

lazy val difflicious = Project("difflicious", file("."))
  .aggregate(allModules.flatMap(_.projectRefs): _*)
  .settings(commonSettings, noPublishSettings)
  .settings(
    compileScoped := Def.inputTaskDyn {
      val args = spaceDelimited("<arg>").parsed
      Def.taskDyn((Test / compile).all(filterByVersionAndPlatform(args.head, args(1))))
    }.evaluated,
    testScoped := Def.inputTaskDyn {
      val args = spaceDelimited("<arg>").parsed
      Def.taskDyn((Test / test).all(filterByVersionAndPlatform(args.head, args(1))))
    }.evaluated,
    publishLocalScoped := Def.inputTaskDyn {
      val args = spaceDelimited("<arg>").parsed
      Def.taskDyn((Compile / publishLocal).all(filterByVersionAndPlatform(args.head, args(1))))
    }.evaluated,
  )

lazy val core = projectMatrix
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(
    name := "difflicious-core",
    libraryDependencies ++= Seq(
      "dev.zio" %% "izumi-reflect" % "2.3.8",
      "com.lihaoyi" %% "fansi" % "0.4.0",
    ) ++ (if (isScala3.value) {
            Seq("com.softwaremill.magnolia1_3" %% "magnolia" % "1.0.0")
          } else
            Seq(
              "com.softwaremill.magnolia1_2" %% "magnolia" % "1.0.0",
              "org.scala-lang" % "scala-reflect" % scala213,
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
      "com.disneystreaming" %% "weaver-core" % weaverVersion,
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
      "org.scalameta" %% "munit-scalacheck" % munitVersion,
    ).map(_ % Test),
  )
  .jvmPlatform(jvmScalaVersions)

lazy val docs: ProjectMatrix = projectMatrix
  .dependsOn(core, coretest, cats, munit, scalatest, weaver)
  .enablePlugins(MicrositesPlugin)
  .settings(
    name := "docs",
    commonSettings,
    publish / skip := true,
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion,
      "com.disneystreaming" %% "weaver-cats" % weaverVersion,
    ),
    makeMicrosite := Def.taskDyn {
      val orig = (ThisProject / makeMicrosite).taskValue
      if (isScala3.value) Def.task({})
      else Def.task(orig.value)
    }.value,
  )
  .settings(
    mdocIn := file("docs/docs"),
    mdocExtraArguments ++= Seq("--noLinkHygiene"),
    micrositeName := "Difflicious",
    micrositeDescription := "Diffs for human consumption",
    micrositeUrl := "https://jatcwang.github.io",
    micrositeBaseUrl := "/difflicious",
    micrositeDocumentationUrl := s"${micrositeBaseUrl.value}/docs/introduction",
    micrositeAuthor := "Jacob Wang",
    micrositeGithubOwner := "jatcwang",
    micrositeGithubRepo := "difflicious",
    micrositeHighlightTheme := "a11y-light",
    micrositePushSiteWith := GitHub4s,
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
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
      val removes = Set("-Wdead-code", "-Ywarn-dead-code") // we use ??? in various places
      (opts ++ extraOpts).filterNot(removes)
    },
  )
  .jvmPlatform(Seq(scala213))

lazy val benchmarks = projectMatrix
  .in(file("modules/benchmarks"))
  .dependsOn(coretest)
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .jvmPlatform(jvmScalaVersions)

lazy val commonSettings = Seq(
  scalacOptions --= {
    if (sys.env.get("CI").isDefined && !isScala3.value) { // TODO: Reenable Scala 3 fatal warnings once nowarn is supported
      Seq.empty
    } else {
      Seq("-Xfatal-warnings")
    }
  },
  versionScheme := Some("early-semver"),
  scalacOptions ++= (if (isScala3.value) Seq.empty[String] else Seq("-Wmacros:after")),
  libraryDependencies ++= Seq(
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
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
    List("ci-release", "publishMicrosite"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
    ),
  ),
)

val setupJekyllSteps = Seq(
  WorkflowStep.Use(
    UseRef.Public("ruby", "setup-ruby", "v1"),
    name = Some("Setup ruby"),
    params = Map("ruby-version" -> "2.7"),
  ),
  WorkflowStep.Run(
    List("gem install jekyll -v 4.1.1"),
    name = Some("Install Jekyll (to build microsite)"),
  ),
)

ThisBuild / githubWorkflowBuildPreamble ++= setupJekyllSteps

ThisBuild / githubWorkflowPublishPreamble ++= setupJekyllSteps

// Add makeMicrosite to the build step
ThisBuild / githubWorkflowBuild ~= { steps =>
  steps.map {
    case w: WorkflowStep.Sbt if w.commands == List("test") =>
      w.copy(commands = List("test", "makeMicrosite", "publishLocal"))
    case w => w
  }
}
// Filter out MacOS and Windows cache steps to make yaml less noisy
ThisBuild / githubWorkflowGeneratedCacheSteps ~= { currentSteps =>
  currentSteps.filterNot(wf => wf.cond.exists(str => str.contains("macos") || str.contains("windows")))
}

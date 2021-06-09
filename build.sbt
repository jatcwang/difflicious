val munitVersion = "0.7.26"
val catsVersion = "2.6.1"
val scalatestVersion = "3.2.9"

val scala213 = "2.13.6"
val scala3 = "3.0.0"

inThisBuild(
  List(
    scalaVersion := scala213,
    crossScalaVersions := Seq(scala213 /*, scala3*/ ),
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

lazy val difflicious = Project("difflicious", file("."))
  .aggregate(core, coretest, benchmarks, cats, docs, munit, scalatest)
  .settings(commonSettings, noPublishSettings)

lazy val core = Project("difflicious-core", file("modules/core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.propensive" %% "magnolia" % "0.17.0",
      "dev.zio" %% "izumi-reflect" % "1.1.1",
      "com.lihaoyi" %% "fansi" % "0.2.12",
    ) ++ (
      if (scalaVersion.value.startsWith("2"))
        Seq("org.scala-lang" % "scala-reflect" % "2.13.5")
      else
        Seq.empty
    ),
    Compile / sourceGenerators += Def.task {
      val file = (Compile / sourceManaged).value / "difflicious" / "TupleDifferInstances.scala"
      IO.write(file, TupleDifferInstancesGen.fileContent)
      Seq(file)
    }.taskValue,
  )

lazy val munit = Project("difflicious-munit", file("modules/munit"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion,
    ),
  )

lazy val scalatest = Project("difflicious-scalatest", file("modules/scalatest"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest-core" % scalatestVersion,
    ),
  )

lazy val cats = Project("difflicious-cats", file("modules/cats"))
  .dependsOn(core, coretest % "test->test")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
    ),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-laws" % catsVersion,
    ).map(_ % Test),
  )

lazy val coretest = Project("coretest", file("modules/coretest"))
  .dependsOn(core)
  .settings(commonSettings, noPublishSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
    ),
    // Test deps
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion,
      "org.scalameta" %% "munit-scalacheck" % munitVersion,
    ).map(_ % Test),
  )

lazy val docs = project
  .dependsOn(core, coretest, cats, munit, scalatest)
  .enablePlugins(MicrositesPlugin)
  .settings(
    commonSettings,
    publish / skip := true,
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion,
    ),
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
    // Disble any2stringAdd deprecation in md files. Seems like mdoc macro generates code which
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

lazy val benchmarks = Project("benchmarks", file("modules/benchmarks"))
  .dependsOn(coretest)
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(noPublishSettings)

lazy val commonSettings = Seq(
  scalacOptions --= {
    if (sys.env.get("CI").isDefined) {
      Seq.empty
    } else {
      Seq("-Xfatal-warnings")
    }
  },
  versionScheme := Some("early-semver"),
  scalacOptions ++= Seq("-Wmacros:after"),
  libraryDependencies ++= Seq(
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.0" cross CrossVersion.full),
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  ).filterNot(_ => scalaVersion.value.startsWith("3")),
)

lazy val noPublishSettings = Seq(
  publish / skip := true,
)

ThisBuild / githubWorkflowJavaVersions := Seq("adopt@1.11")
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
    UseRef.Public("actions", "setup-ruby", "v1"),
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
      w.copy(commands = List("test", "makeMicrosite"))
    case w => w
  }
}
// Filter out MacOS and Windows cache steps to make yaml less noisy
ThisBuild / githubWorkflowGeneratedCacheSteps ~= { currentSteps =>
  currentSteps.filterNot(wf => wf.cond.exists(str => str.contains("macos") || str.contains("windows")))
}

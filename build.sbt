import sbtghactions.JavaSpec
import complete.DefaultParsers.*
import sbt.Reference.display
import org.typelevel.sbt.tpolecat.{CiMode, DevMode}
import scala.concurrent.duration.*
import scala.sys.process.Process
import snapshot4s.BuildInfo.snapshot4sVersion

val munitVersion = "1.3.5"
val munitScalacheckVersion = "1.3.0"
val catsVersion = "2.13.0"
val circeVersion = "0.14.16"
val declineVersion = "2.6.2"
val jlineVersion = "4.4.0"
val scalatestVersion = "3.2.20"
val weaverVersion = "0.13.0"
val hearthVersion = "0.4.2"
val jsoniterScalaVersion = "2.40.1"

val generateCompileBenchmarkSources = taskKey[Seq[File]]("Generate tracked compile benchmark sources")

def runWebsiteCommand(command: Seq[String], cwd: File, extraEnv: (String, String)*): Unit = {
  val exit = Process(command, cwd, extraEnv *).!
  assert(exit == 0, s"command returned $exit: ${command.mkString(" ")}")
}

def runWebsiteCommandWithRetry(
  command: Seq[String],
  cwd: File,
  retries: Int,
  extraEnv: (String, String)*,
): Unit = {
  var attemptsLeft = retries
  var exit = Process(command, cwd, extraEnv *).!
  while (exit != 0 && attemptsLeft > 0) {
    println(s"Retrying command after exit $exit: ${command.mkString(" ")}")
    attemptsLeft -= 1
    exit = Process(command, cwd, extraEnv *).!
  }
  assert(exit == 0, s"command returned $exit: ${command.mkString(" ")}")
}

val isScala3 = Def.setting {
  // doesn't work well with >= 3.0.0 for `3.0.0-M1`
  scalaVersion.value.startsWith("3")
}

val mainScalaVersion = Build.Scala213
val scalaCrossVersions = Seq(Build.Scala213, Build.Scala3)

inThisBuild(
  List(
    scalaVersion := mainScalaVersion,
    organization := "com.github.jatcwang",
    homepage := Some(uri("https://github.com/jatcwang/difflicious")),
    licenses := List("Apache-2.0" -> uri("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jatcwang",
        "Jacob Wang",
        "jatcwang@gmail.com",
        uri("https://almostfunctional.com"),
      ),
    ),
    commands ++= Build.createBuildCommands(projectMatrixModules.flatMap(_.projectRefs)),
  ),
)

addCommandAlias("runBenchCompile2", "benchmarkCompile / clean ; benchmarkCompile / Compile / compile")
addCommandAlias("runBenchCompile3", "benchmarkCompile3 / clean ; benchmarkCompile3 / Compile / compile")
addCommandAlias("test2", "coretest/test")
addCommandAlias("test3", "coretest3/test")

lazy val projectMatrixModules =
  Seq(core, coretest, munit, scalatest, weaver, cats, circe, reporterCore, cli, benchmarks, docs)

lazy val allModules =
  projectMatrixModules.flatMap(_.projectRefs) :+ LocalProject("sbtPlugin")

lazy val difflicious = Project("difflicious", file("."))
  .aggregate(allModules *)
  .settings(commonSettings, noPublishSettings)

lazy val sbtPlugin = project
  .in(file("modules/sbt-plugin"))
  .enablePlugins(SbtPlugin, ScriptedPlugin)
  .settings(selfHostedDiffliciousViewerSettings)
  .settings(
    name := "sbt-difflicious",
    sbt.Keys.sbtPlugin := true,
    addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.2.0"),
    scalaVersion := Build.Scala212,
    crossScalaVersions := Seq(Build.Scala212, Build.Scala3),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.12.11"
        case _ => "2.0.7"
      }
    },
    versionScheme := Some("early-semver"),
    Compile / resourceGenerators += Def.task {
      val output = (Compile / resourceManaged).value / "sbt-difflicious.properties"
      val properties = new java.util.Properties()
      properties.put("version", version.value)
      IO.write(properties, "sbt-difflicious properties", output)
      Seq(output)
    }.taskValue,
    scriptedDependencies := {
      (LocalProject("core") / publishLocal).value
      (LocalProject("reporterCore") / publishLocal).value
      (LocalProject("circe") / publishLocal).value
      (LocalProject("core3") / publishLocal).value
      (LocalProject("reporterCore3") / publishLocal).value
      (LocalProject("circe3") / publishLocal).value
      (LocalProject("munit3") / publishLocal).value
      (LocalProject("scalatest3") / publishLocal).value
      (LocalProject("weaver3") / publishLocal).value
      (LocalProject("cli3") / publishLocal).value
      scriptedDependencies.value
    },
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq(s"-Dplugin.version=${version.value}"),
  )

lazy val core = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(
    name := "difflicious-core",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "fansi" % "0.5.1",
      "com.kubuszok" %% "hearth" % hearthVersion,
    ) ++ (if (isScala3.value) {
            Seq(compilerPlugin("com.kubuszok" %% "hearth-cross-quotes" % hearthVersion))
          } else
            Seq("org.scala-lang" % "scala-reflect" % Build.Scala213)),
    Compile / sourceGenerators += Def.task {
      val file = (Compile / sourceManaged).value / "difflicious" / "TupleDifferInstances.scala"
      IO.write(file, TupleDifferInstancesGen.fileContent)
      Seq(file)
    }.taskValue,
  )
  .jvmPlatform(scalaCrossVersions)
  .jsPlatform(
    scalaCrossVersions,
    settings = Seq(
      libraryDependencies ++= Seq(
        "org.scala-js" %% "scalajs-dom" % "2.8.1",
      ),
    ),
  )
  .nativePlatform(scalaCrossVersions)

lazy val munit = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .in(file("modules/munit"))
  .dependsOn(core, reporterCore)
  .settings(commonSettings)
  .settings(
    name := "difflicious-munit",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion,
    ),
  )
  .jvmPlatform(scalaCrossVersions)
  .jsPlatform(scalaCrossVersions)
  .nativePlatform(scalaCrossVersions)

lazy val scalatest = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .in(file("modules/scalatest"))
  .dependsOn(core, reporterCore)
  .settings(commonSettings)
  .settings(
    name := "difflicious-scalatest",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest-core" % scalatestVersion,
    ),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion,
    ).map(_ % Test),
  )
  .jvmPlatform(
    scalaCrossVersions,
    Seq(
      libraryDependencies +=
        "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterScalaVersion % Provided,
      libraryDependencies += "io.circe" %% "circe-parser" % circeVersion % Test,
      libraryDependencies += "org.scalatest" %% "scalatest-funsuite" % scalatestVersion % Test,
      libraryDependencies += "org.scalatest" %% "scalatest-freespec" % scalatestVersion % Test,
    ),
  )
  .jsPlatform(scalaCrossVersions)
  .nativePlatform(scalaCrossVersions)

lazy val weaver = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .in(file("modules/weaver"))
  .dependsOn(core, reporterCore)
  .settings(commonSettings)
  .settings(
    name := "difflicious-weaver",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "weaver-core" % weaverVersion,
    ),
  )
  .jvmPlatform(scalaCrossVersions)
  .jsPlatform(scalaCrossVersions)
  .nativePlatform(scalaCrossVersions)

lazy val cats = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
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
  .jvmPlatform(scalaCrossVersions)
  .jsPlatform(scalaCrossVersions)
  .nativePlatform(scalaCrossVersions)

lazy val circe = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .in(file("modules/circe"))
  .dependsOn(core, coretest % "test->test")
  .settings(commonSettings)
  .settings(
    name := "difflicious-circe",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
    ),
  )
  .jvmPlatform(scalaCrossVersions)
  .jsPlatform(scalaCrossVersions)
  .nativePlatform(scalaCrossVersions)

lazy val reporterCore = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .in(file("modules/reporter-core"))
  .dependsOn(core)
  .enablePlugins(ShadingPlugin)
  .settings(commonSettings)
  .settings(
    name := "difflicious-reporter-core",
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % jsoniterScalaVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterScalaVersion % Provided,
    ),
    shadedDependencies ++= {
      if (virtualAxes.value.contains(VirtualAxis.jvm))
        Set(
          "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % jsoniterScalaVersion,
        )
      else Set.empty
    },
    shadingRules ++= {
      if (virtualAxes.value.contains(VirtualAxis.jvm))
        Seq(
          ShadingRule.moveUnder(
            "com.github.plokhotnyuk.jsoniter_scala",
            "difflicious.internal.shaded",
          ),
        )
      else Seq.empty
    },
    validNamespaces += "difflicious",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-parser" % circeVersion,
      "org.scalameta" %% "munit" % munitVersion,
    ).map(_ % Test),
  )
  .jvmPlatform(scalaCrossVersions)
  .jsPlatform(scalaCrossVersions)
  .nativePlatform(scalaCrossVersions)

lazy val cli = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .in(file("modules/cli"))
  .dependsOn(reporterCore, circe)
  .enablePlugins(GraalVMNativeImagePlugin, Snapshot4sPlugin)
  .settings(commonSettings)
  .settings(
    name := "difflicious-cli",
    libraryDependencies ++= Seq(
      "com.monovore" %% "decline" % declineVersion,
      "org.typelevel" %% "cats-core" % catsVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "org.jline" % "jline-terminal-jni" % jlineVersion,
      "org.scalameta" %% "munit" % munitVersion % Test,
      "com.siriusxm" %% "snapshot4s-munit" % snapshot4sVersion % Test,
    ),
    Compile / mainClass := Some("difflicious.cli.Main"),
    GraalVMNativeImage / name := "difflicious",
    GraalVMNativeImage / graalVMNativeImageOptions ++= Seq(
      "--no-fallback",
      "-H:+ReportExceptionStackTraces",
    ),
    Compile / run / fork := true,
    Compile / run / baseDirectory := (ThisBuild / baseDirectory).value,
    Compile / run / connectInput := true,
  )
  .jvmPlatform(Seq(Build.Scala3))

lazy val example = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .in(file("modules/example"))
  .dependsOn(scalatest, munit, weaver, cats)
  .settings(commonSettings, noPublishSettings)
  .settings(
    name := "example",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
      "org.scalatest" %% "scalatest-funsuite" % scalatestVersion % Test,
      "org.typelevel" %% "weaver-cats" % weaverVersion % Test,
    ),
  )
  .jvmPlatform(Seq(Build.Scala3))

lazy val coretest = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
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
  .jvmPlatform(scalaCrossVersions)
  .jsPlatform(scalaCrossVersions)
  .nativePlatform(scalaCrossVersions)

lazy val docs: ProjectMatrix = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .dependsOn(core, coretest, cats, circe, munit, scalatest, weaver)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(
    name := "docs",
    moduleName := "difflicious-docs",
    commonSettings,
    publish / skip := true,
  )
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-literal" % circeVersion,
      "org.scalatest" %% "scalatest" % scalatestVersion,
      "org.typelevel" %% "weaver-cats" % weaverVersion,
    ),
  )
  .settings(
    mdocIn := file("docs/docs"),
    mdocOut := file("docs/target/mdoc"),
    mdocVariables := Map("VERSION" -> sys.env.get("DOCS_VERSION").filter(_.nonEmpty).getOrElse(version.value)),
    mdocExtraArguments ++= Seq("--noLinkHygiene"),
    docusaurusCreateSite := {
      val website = (ThisBuild / baseDirectory).value / "website"
      runWebsiteCommand(Seq("yarn", "install", "--immutable"), website)
      runWebsiteCommandWithRetry(Seq("yarn", "run", "build"), website, retries = 1)
      website / "build"
    },
    docusaurusPublishGhpages := {
      val website = (ThisBuild / baseDirectory).value / "website"
      val publishEnv = Seq(
        "GIT_USER" -> sys.env.getOrElse("GIT_USER", "jatcwang"),
        "GIT_PASS" -> sys.env.getOrElse("GIT_PASS", sys.env.getOrElse("GITHUB_TOKEN", "")),
        "GIT_USER_NAME" -> sys.env.getOrElse("GIT_USER_NAME", "jatcwang"),
        "GIT_USER_EMAIL" -> sys.env.getOrElse(
          "GIT_USER_EMAIL",
          "jatcwang@gmail.com",
        ),
      ).filter(_._2.nonEmpty)
      runWebsiteCommand(Seq("yarn", "install", "--immutable"), website)
      runWebsiteCommand(Seq("yarn", "run", "publish-gh-pages"), website, publishEnv *)
    },
  )
  .settings(
    // Disable any2stringAdd deprecation in md files. Seems like mdoc macro generates code which
    // use implicit conversion to string
    scalacOptions ~= { opts =>
      val extraOpts =
        Seq(
          "-Wconf:msg=.*method any2stringadd.*:i",
          "-Wconf:msg=.*The outer reference in this type test.*:s",
          "-Wconf:msg=.*method right in class Either.*:s",
          "-Wconf:msg=.*method get in class RightProjection.*:s",
          "-Wconf:msg=.*local (object|class).+?is never used:s",
        )
      val removes = Set("-Wdead-code", "-Ywarn-dead-code", "-Wnonunit-statement") // we use ??? in various places
      (opts ++ extraOpts).filterNot(removes)
    },
  )
  .jvmPlatform(Seq(Build.Scala213))

lazy val benchmarks = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .in(file("modules/benchmarks"))
  .dependsOn(coretest)
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .jvmPlatform(scalaCrossVersions)

lazy val benchmarkCompile = projectMatrix
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Build.Scala213))
  .in(file("modules/compile-benchmarks"))
  .dependsOn(core)
  .settings(commonSettings, noPublishSettings)
  .settings(
    name := "difflicious-compile-benchmarks",
    generateCompileBenchmarkSources := Def.uncached {
      val directory = (Compile / scalaSource).value / "difflicious" / "derivation"
      IO.createDirectory(directory)
      DerivationBenchmarkInputsGen.files(layerCount = 25).map { case (fileName, content) =>
        val file = directory / fileName
        IO.write(file, content)
        file
      }
    },
    Compile / sourceGenerators += generateCompileBenchmarkSources.taskValue,
  )
  .jvmPlatform(scalaCrossVersions)

lazy val selfHostedDiffliciousViewerSettings = Seq(
  diffliciousCliAutoDependency := false,
  diffliciousViewer := Def.inputTaskDyn {
    val parsedArguments = sbt.complete.DefaultParsers.spaceDelimited("<arg>").parsed
    val arguments = (diffliciousViewerAdditionalArguments.value ++ parsedArguments)
      .map(argument => s""""${argument.replace("\\", "\\\\").replace("\"", "\\\"")}"""")
      .mkString(" ", " ", "")
    (LocalProject("cli3") / Compile / run).toTask(arguments).map(_ => ())
  }.evaluated,
)

lazy val commonSettings = selfHostedDiffliciousViewerSettings ++ Seq(
  versionScheme := Some("early-semver"),
  libraryDependencySchemes ++= Seq(
    "org.scala-native" % "test-interface_native0.5_2.13" % VersionScheme.Always,
    "org.scala-native" % "test-interface_native0.5_3" % VersionScheme.Always,
  ),
  nativeConfig ~= { config =>
    // Avoid GNU ld executable-stack warnings from Scala Native runtime assembly objects.
    val linuxNoExecStackLinkerOption = "-Wl,-z,noexecstack"
    if (scala.util.Properties.isLinux && !config.linkingOptions.contains(linuxNoExecStackLinkerOption))
      config.withLinkingOptions(config.linkingOptions :+ linuxNoExecStackLinkerOption)
    else config
  },
  // Uncomment to get derivation scope timings in combination of "import difflicious.debug.implicits.logDerivation"
//  scalacOptions ++= Seq(
//    "-Xmacro-settings:hearth.mioBenchmarkScopes=true",
//    "-Xmacro-settings:hearth.mioBenchmarkFlameGraphDir=/tmp/hearth-bench",
//  ),
  scalacOptions ++= (if (isScala3.value)
                       Seq.empty
                     else
                       Seq(
                         "-Wmacros:after",
                         "-Xsource:3",
                         "-Wconf:msg=.*access modifiers for.*:s", // silence warnings about coyp method on case class with private constructors
                       )),
  // TODO: Not sure why but having these scalac options seems to crash the compiler complaining about
  // duplicate top level definitions in generated class files.
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
  scalacOptions ++= (if (isScala3.value)
                       Seq.empty
                     else
                       Seq(
                         "-Wconf:cat=unused-nowarn:s",
                         "-Wconf:msg=.* in method workaround_[0-9]+ is never used:s",
                       )),
  libraryDependencies ++= Seq(
    compilerPlugin(("org.typelevel" %% "kind-projector" % "0.13.4").cross(CrossVersion.full)),
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  ).filterNot(_ => isScala3.value),
)

lazy val noPublishSettings = Seq(
  publish / skip := true,
)

lazy val setupMise = Seq(
  WorkflowStep.Use(
    UseRef.Public("jdx", "mise-action", "v4"),
    name = Some("Setup mise"),
  ),
)

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowTargetTags := Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches :=
  Seq(RefPredicate.StartsWith(Ref.Tag("v")))

ThisBuild / githubWorkflowJobSetup :=
  Seq(WorkflowStep.CheckoutFull) ++
    setupMise ++
    Seq(WorkflowStep.SetupSbt()) ++
    githubWorkflowGeneratedCacheSteps.value

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(
    List("test", "docs/mdoc", "docs/docusaurusCreateSite"),
    name = Some("Build project and docs"),
  ),
  WorkflowStep.Sbt(
    List(s"++${Build.Scala3}", "sbtPlugin / Compile / compile"),
    name = Some("Compile sbt 2 plugin with fatal warnings"),
  ),
)

ThisBuild / githubWorkflowAddedJobs := Seq(
  WorkflowJob(
    id = "native",
    name = "Build Native Executables",
    steps = List(
      WorkflowStep.CheckoutFull,
      setupMise.head,
      WorkflowStep.SetupSbt(),
      WorkflowStep.Run(
        List("mise install java@graalvm-community-25.0.1"),
        name = Some("Install GraalVM"),
      ),
      WorkflowStep.Run(
        List(
          "mise exec java@graalvm-community-25.0.1 -- sbt --client \"cli3/test ; cli3/GraalVMNativeImage/packageBin\"",
        ),
        name = Some("Build and test native image"),
      ),
      WorkflowStep.Run(
        List(
          "set -euo pipefail",
          "binary=\"$(find target/out -type f -path '*/difflicious-cli/graalvm-native-image/difflicious' -print -quit)\"",
          "test -n \"$binary\"",
          "test -x \"$binary\"",
          "\"$binary\" --help >/dev/null",
          "mkdir -p dist",
          "cp \"$binary\" \"dist/${{ matrix.asset }}\"",
          "chmod 755 \"dist/${{ matrix.asset }}\"",
          "(cd dist && shasum -a 256 \"${{ matrix.asset }}\" > \"${{ matrix.asset }}.sha256\")",
        ),
        name = Some("Verify and stage native image"),
      ),
      WorkflowStep.Use(
        UseRef.Public("actions", "upload-artifact", "v7"),
        name = Some("Upload native image"),
        params = Map(
          "name" -> "native-${{ matrix.asset }}",
          "path" -> "dist/*",
          "if-no-files-found" -> "error",
        ),
      ),
    ),
    cond = Some(
      "github.ref == 'refs/heads/master' || startsWith(github.ref, 'refs/tags/v')",
    ),
    oses = List("ubuntu-24.04", "ubuntu-24.04-arm", "macos-15"),
    scalas = List("3"),
    javas = List(JavaSpec.temurin("17")),
    matrixFailFast = Some(false),
    matrixIncs = List(
      MatrixInclude(
        matching = Map("os" -> "ubuntu-24.04"),
        additions = Map("asset" -> "difflicious-linux-x86_64"),
      ),
      MatrixInclude(
        matching = Map("os" -> "ubuntu-24.04-arm"),
        additions = Map("asset" -> "difflicious-linux-aarch64"),
      ),
      MatrixInclude(
        matching = Map("os" -> "macos-15"),
        additions = Map("asset" -> "difflicious-macos-aarch64"),
      ),
    ),
    timeout = Some(60.minutes),
  ),
)

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Use(
    UseRef.Public("actions", "download-artifact", "v8"),
    name = Some("Download native executables"),
    params = Map(
      "pattern" -> "native-*",
      "path" -> "dist",
      "merge-multiple" -> "true",
    ),
  ),
  WorkflowStep.Run(
    List(
      """test "$(find dist -maxdepth 1 -type f ! -name '*.sha256' | wc -l)" -eq 3""",
      """test "$(find dist -maxdepth 1 -type f -name '*.sha256' | wc -l)" -eq 3""",
      """for checksum in dist/*.sha256; do (cd dist && sha256sum -c "$(basename "$checksum")"); done""",
    ),
    name = Some("Validate native executables"),
  ),
  WorkflowStep.Sbt(
    List("ci-release"),
    name = Some("Publish artifacts"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
    ),
  ),
  WorkflowStep.Run(
    List("""gh release upload "$GITHUB_REF_NAME" dist/* --clobber"""),
    name = Some("Attach native executables to GitHub release"),
    env = Map("GH_TOKEN" -> "${{ github.token }}"),
  ),
  WorkflowStep.Run(
    List(
      """if [[ "$GITHUB_REF_NAME" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then""",
      "  echo \"official=true\" >> \"$GITHUB_OUTPUT\"",
      "else",
      "  echo \"official=false\" >> \"$GITHUB_OUTPUT\"",
      "fi",
    ),
    name = Some("Classify release tag"),
    id = Some("release-tag"),
  ),
  WorkflowStep.Sbt(
    List("docs/docusaurusPublishGhpages"),
    name = Some("Publish docs"),
    cond = Some("steps.release-tag.outputs.official == 'true'"),
    env = Map(
      "CURRENT_BRANCH" -> "${{ github.ref_name }}",
      "GIT_PASS" -> "${{ secrets.GITHUB_TOKEN }}",
      "GIT_USER" -> "github-actions[bot]",
      "GIT_USER_EMAIL" -> "41898282+github-actions[bot]@users.noreply.github.com",
      "GIT_USER_NAME" -> "github-actions[bot]",
    ),
  ),
)

ThisBuild / githubWorkflowGeneratedCI ~= { jobs =>
  jobs.map {
    case job if job.id == "publish" =>
      job.copy(
        needs = List("build", "native"),
        permissions = Some(
          Permissions.Specify(
            Map(
              PermissionScope.Contents -> PermissionValue.Write,
            ),
          ),
        ),
      )
    case job => job
  }
}

ThisBuild / githubWorkflowScalaVersions := Seq("all")

ThisBuild / githubWorkflowBuildSbtStepPreamble := Seq.empty

ThisBuild / githubWorkflowArtifactUpload := false

// Filter out MacOS and Windows cache steps to make yaml less noisy
ThisBuild / githubWorkflowGeneratedCacheSteps ~= { currentSteps =>
  currentSteps.filterNot(wf => wf.cond.exists(str => str.contains("macos") || str.contains("windows")))
}

import difflicious.sbt.DiffliciousPlugin.autoImport.*
import difflicious.sbt.DiffliciousPlugin.SysPropNames
import sbtcompat.PluginCompat.*

ThisBuild / scalaVersion := "3.8.4"

val testDependencies = Seq(
  "com.github.jatcwang" %% "difflicious-scalatest" % sys.props("plugin.version"),
  "org.scalatest" %% "scalatest-funsuite" % "3.2.20",
).map(_ % Test)

lazy val checkForkSettings = taskKey[Unit]("Verify DiffliciousPlugin does not modify Test / fork")
lazy val checkReportAllOutputDirs = taskKey[Unit]("Verify aggregated Difflicious report output directories")
lazy val checkCliDependency = taskKey[Unit]("Verify the Difflicious CLI dependency is resolved")
lazy val checkSharedRunId = taskKey[Unit]("Verify forked and non-forked modules share the build run id")
lazy val checkSbtJvmRunId = taskKey[Unit]("Verify setDiffliciousRunId writes the shared run id to the sbt JVM")
lazy val cleanReports = taskKey[Unit]("Delete Difflicious JSONL reports")
lazy val createViewerReportDirs = taskKey[Unit]("Create the report directories passed to the viewer")
lazy val checkForkedIndividualTestRunIdsDiffer =
  taskKey[Unit]("Verify two separate forked module test runs get different run ids")
lazy val checkUnforkedIndividualTestRunIdsDiffer =
  taskKey[Unit]("Verify two separate unforked module test runs get different run ids")
lazy val checkRootTestRunIdAfterFirstRun =
  taskKey[Unit]("Verify the first aggregate root test run gives both modules the same run id")
lazy val checkRootTestRunIdAfterSecondRun =
  taskKey[Unit]("Verify the second aggregate root test run gives both modules the same new run id")

lazy val root = project
  .in(file("."))
  .aggregate(forked, unforked)
  .settings(
    cleanReports := Def.uncached {
      IO.delete((forked / target).value / "difflicious-report")
      IO.delete((unforked / target).value / "difflicious-report")
    },
    createViewerReportDirs := Def.uncached {
      Seq(
        target.value / "difflicious-report",
        (forked / target).value / "difflicious-report",
        (unforked / target).value / "difflicious-report",
      ).foreach(IO.createDirectory)
    },
    checkForkSettings := Def.uncached {
      assert((forked / Test / fork).value, "forked / Test / fork should remain true")
      assert(!(unforked / Test / fork).value, "unforked / Test / fork should remain false")
    },
    checkReportAllOutputDirs := Def.uncached {
      val actual = (Test / diffliciousReportAllOutputDirs).value
      val expected = Seq(
        target.value / "difflicious-report",
        (forked / target).value / "difflicious-report",
        (unforked / target).value / "difflicious-report",
      )
      assert(
        actual.toSet == expected.toSet && actual.size == expected.size,
        s"root report output directories should include itself and all aggregated projects: $actual",
      )
      val viewerArguments = diffliciousViewerAdditionalArguments.value
      val viewerDirectories = viewerArguments.grouped(2).collect {
        case Seq("-d", directory) => file(directory)
      }.toSeq
      assert(
        viewerArguments.size == viewerDirectories.size * 2 && viewerDirectories.toSet == expected.toSet,
        s"viewer arguments should pass each report output directory with -d: $viewerArguments",
      )
    },
    checkCliDependency := Def.uncached {
      val cliModules = update.value.allModules.filter(_.name == "difflicious-cli_2.13")
      assert(!(diffliciousViewer / aggregate).value, "diffliciousViewer should run only once for an aggregate project")
      assert(
        cliModules.exists(_.revision == sys.props("plugin.version")),
        s"expected difflicious-cli_2.13 at the plugin version, got $cliModules",
      )
    },
    checkSharedRunId := Def.uncached {
      val runId = (ThisBuild / diffliciousCurrentRunId).value
      val forkedRunIdOption = s"-D${SysPropNames.RUN_ID}=$runId"
      val unforkedRunIdOption = s"-D${SysPropNames.RUN_ID}=$runId"
      val forkedOutputDir = ((forked / target).value / "difflicious-report").getAbsolutePath
      val unforkedOutputDir = ((unforked / target).value / "difflicious-report").getAbsolutePath
      val forkedOutputDirOption = s"-D${SysPropNames.REPORT_OUTPUT_DIR}=$forkedOutputDir"
      val unforkedOutputDirOption = s"-D${SysPropNames.REPORT_OUTPUT_DIR}=$unforkedOutputDir"

      assert(runId.nonEmpty, "ThisBuild / diffliciousCurrentRunId should be non-empty")
      assert(
        (forked / Test / javaOptions).value.contains(forkedRunIdOption),
        s"forked / Test / javaOptions should contain $forkedRunIdOption",
      )
      assert(
        (unforked / Test / javaOptions).value.contains(unforkedRunIdOption),
        s"unforked / Test / javaOptions should contain $unforkedRunIdOption",
      )
      assert(
        (forked / Test / javaOptions).value.contains(forkedOutputDirOption),
        s"forked / Test / javaOptions should contain $forkedOutputDirOption",
      )
      assert(
        (unforked / Test / javaOptions).value.contains(unforkedOutputDirOption),
        s"unforked / Test / javaOptions should contain $unforkedOutputDirOption",
      )
    },
    checkSbtJvmRunId := Def.uncached {
      val runId = (ThisBuild / diffliciousCurrentRunId).value
      (forked / Test / setDiffliciousRunId).value
      assert(
        sys.props.get(SysPropNames.RUN_ID).contains(runId),
        "forked / Test / setDiffliciousRunId should write the build run id",
      )
      (unforked / Test / setDiffliciousRunId).value
      assert(
        sys.props.get(SysPropNames.RUN_ID).contains(runId),
        "unforked / Test / setDiffliciousRunId should write the same build run id",
      )
    },
    checkForkedIndividualTestRunIdsDiffer := Def.uncached {
      assertDifferentRunIds(
        readRunIds((forked / target).value / "difflicious-report"),
        "separate forked/test runs should use different run ids",
      )
    },
    checkUnforkedIndividualTestRunIdsDiffer := Def.uncached {
      assertDifferentRunIds(
        readRunIds((unforked / target).value / "difflicious-report"),
        "separate unforked/test runs should use different run ids",
      )
    },
    checkRootTestRunIdAfterFirstRun := Def.uncached {
      checkRootTestRunIds(
        expectedRuns = 1,
        baseDir = (ThisBuild / baseDirectory).value,
        clue = "first aggregate test run",
      )
    },
    checkRootTestRunIdAfterSecondRun := Def.uncached {
      checkRootTestRunIds(
        expectedRuns = 2,
        baseDir = (ThisBuild / baseDirectory).value,
        clue = "second aggregate test run",
      )
      assertDifferentRunIds(
        readSuiteRunIds((ThisBuild / baseDirectory).value, "example.ForkedSuite"),
        "separate aggregate test runs should use different run ids",
      )
    },
  )

lazy val forked = project
  .in(file("forked"))
  .settings(
    libraryDependencies ++= testDependencies,
    Test / fork := true,
  )

lazy val unforked = project
  .in(file("unforked"))
  .settings(
    libraryDependencies ++= testDependencies,
    Test / fork := false,
  )

def readRunIds(outputDir: File): Vector[String] = {
  val jsonlFiles = (outputDir ** "*.jsonl").get().sortBy(_.getAbsolutePath)
  readRunIdsFromFiles(jsonlFiles)
}

def readSuiteRunIds(baseDir: File, suiteName: String): Vector[String] =
  readRunIdsFromFiles((baseDir ** s"$suiteName.jsonl").get())

def readRunIdsFromFiles(jsonlFiles: Seq[File]): Vector[String] = {
  val RunId = """"runId"\s*:\s*"([^"]+)"""".r
  jsonlFiles.flatMap { file =>
    IO.readLines(file).flatMap { line =>
      RunId.findFirstMatchIn(line).map(_.group(1))
    }
  }.distinct.sorted.toVector
}

def assertDifferentRunIds(runIds: Vector[String], clue: String): Unit = {
  assert(runIds.size == 2, s"$clue: expected two run ids, got $runIds")
  assert(runIds(0) != runIds(1), s"$clue: got duplicate run id ${runIds(0)}")
}

def checkRootTestRunIds(
  expectedRuns: Int,
  baseDir: File,
  clue: String,
): Unit = {
  val forkedRunIds = readSuiteRunIds(baseDir, "example.ForkedSuite")
  val unforkedRunIds = readSuiteRunIds(baseDir, "example.UnforkedSuite")
  val jsonlFiles = (baseDir ** "*.jsonl").get().map { file =>
    file.relativeTo(baseDir).getOrElse(file).getPath
  }.sorted
  assert(
    forkedRunIds.size == expectedRuns,
    s"$clue: expected $expectedRuns forked run ids, got $forkedRunIds; jsonl files: $jsonlFiles",
  )
  assert(
    unforkedRunIds.size == expectedRuns,
    s"$clue: expected $expectedRuns unforked run ids, got $unforkedRunIds; jsonl files: $jsonlFiles",
  )
  assert(forkedRunIds.forall(_.nonEmpty), s"$clue: forked run ids should be non-empty: $forkedRunIds")
  assert(unforkedRunIds.forall(_.nonEmpty), s"$clue: unforked run ids should be non-empty: $unforkedRunIds")
  assert(
    forkedRunIds.zip(unforkedRunIds).forall { case (forkedRunId, unforkedRunId) => forkedRunId == unforkedRunId },
    s"$clue: forked and unforked modules should share the run id within each aggregate test run: $forkedRunIds vs $unforkedRunIds",
  )
}

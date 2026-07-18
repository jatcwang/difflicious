package difflicious.sbt

import sbt.*
import sbt.Keys.*
import sbtcompat.PluginCompat.*

object DiffliciousPlugin extends AutoPlugin {
  private val DiffliciousCli = config("diffliciousCli").hide

  object autoImport {
    val diffliciousOverrideRunId = settingKey[Option[String]]("Optional Difflicious run id override for this build")
    val diffliciousGenerateRunId = taskKey[String]("Generate a fresh Difflicious run id for the current test run")
    val diffliciousCurrentRunId = taskKey[String]("Difflicious run id for the current test run")
    val setDiffliciousRunId = taskKey[Unit]("Set the current Difflicious run id in the sbt JVM")
    val diffliciousReportOutputDir =
      settingKey[File]("Directory where Difflicious writes reports")
    val diffliciousReportAllOutputDirs =
      settingKey[Seq[File]]("Difflicious report directories for this project and all aggregated projects")
    val diffliciousCliAutoDependency =
      settingKey[Boolean]("Whether to add the Difflicious CLI as a managed dependency")
    val diffliciousViewerAdditionalArguments =
      settingKey[Seq[String]]("Additional arguments passed to the Difflicious report viewer")
    val diffliciousViewer =
      inputKey[Unit]("Open Difflicious reports for this project and all aggregated projects")
    val diffliciousScalaTestJsonlReporterEnabled =
      settingKey[Boolean]("Whether to register the Difflicious ScalaTest JSONL reporter")
  }

  import autoImport.*

  override def trigger = allRequirements

  override def projectConfigurations: Seq[Configuration] = Seq(DiffliciousCli)

  override def buildSettings: Seq[Def.Setting[_]] =
    Seq(
      ThisBuild / diffliciousOverrideRunId := None,
      ThisBuild / diffliciousCliAutoDependency := true,
      ThisBuild / diffliciousGenerateRunId := Def.uncached {
        freshUlid()
      },
      ThisBuild / diffliciousCurrentRunId := (ThisBuild / diffliciousOverrideRunId).value.getOrElse(
        (ThisBuild / diffliciousGenerateRunId).value,
      ),
    )

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      Test / diffliciousReportOutputDir := target.value / "difflicious-report",
      Test / diffliciousReportAllOutputDirs := (Test / diffliciousReportOutputDir)
        .all(ScopeFilter(inAggregates(ThisProject, includeRoot = true)))
        .value,
      diffliciousCliAutoDependency := (ThisBuild / diffliciousCliAutoDependency).value,
      diffliciousViewerAdditionalArguments := (Test / diffliciousReportAllOutputDirs).value.flatMap(directory =>
        Seq("-d", directory.getAbsolutePath),
      ),
      diffliciousViewer / aggregate := false,
      libraryDependencies ++= {
        if (diffliciousCliAutoDependency.value)
          Seq(
            "com.github.jatcwang" % "difflicious-cli_3" % PluginBuildInfo.version % DiffliciousCli,
          )
        else Seq.empty
      },
      diffliciousViewer := Def.inputTaskDyn {
        val parsedArguments = sbt.complete.DefaultParsers.spaceDelimited("<arg>").parsed
        val arguments = (diffliciousViewerAdditionalArguments.value ++ parsedArguments).map(quoteCommandArgument)
        val project = thisProjectRef.value
        ClientJobCompat.run(project, DiffliciousCli, arguments.mkString(" "))
      }.evaluated,
      Test / diffliciousScalaTestJsonlReporterEnabled := true,
      Test / setDiffliciousRunId := {
        val runId = (ThisBuild / diffliciousCurrentRunId).value
        val outputDir = (Test / diffliciousReportOutputDir).value
        sys.props.update(SysPropNames.RUN_ID, runId)
        sys.props.update(SysPropNames.REPORT_OUTPUT_DIR, outputDir.getAbsolutePath)
      },
      Test / javaOptions ++= Seq(
        s"-D${SysPropNames.RUN_ID}=${(ThisBuild / diffliciousCurrentRunId).value}",
        s"-D${SysPropNames.REPORT_OUTPUT_DIR}=${(Test / diffliciousReportOutputDir).value.getAbsolutePath}",
      ),
      Test / testOptions ++= {
        (Test / setDiffliciousRunId).value
        if ((Test / diffliciousScalaTestJsonlReporterEnabled).value)
          Seq(
            Tests.Argument(
              TestFrameworks.ScalaTest,
              "-C",
              "difflicious.scalatest.DiffResultJsonlReporter",
            ),
          )
        else Seq.empty
      },
    ) ++ ClientJobCompat.configurationSettings(DiffliciousCli) ++ inConfig(DiffliciousCli)(
      Seq(
        managedClasspath := Def.uncached {
          implicit val converter: xsbti.FileConverter = fileConverter.value
          toAttributedFiles(update.value.matching(configurationFilter(DiffliciousCli.name)))
        },
        fullClasspath := Def.uncached(managedClasspath.value),
        fullClasspathAsJars := Def.uncached(managedClasspath.value),
        exportedProductJars := Def.uncached(Seq.empty),
        run / mainClass := Some("difflicious.cli.Main"),
        run / fork := true,
        run / forkOptions := Def.uncached(
          ForkOptions()
            .withOutputStrategy(StdoutOutput)
            .withWorkingDirectory((ThisBuild / baseDirectory).value)
            .withConnectInput(true),
        ),
      ),
    )

  private def quoteCommandArgument(argument: String): String =
    "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def freshUlid(): String = {
    val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    val chars = new Array[Char](26)
    val random = new Array[Byte](10)
    java.util.concurrent.ThreadLocalRandom.current().nextBytes(random)

    var timestamp = System.currentTimeMillis()
    var timestampIndex = 9
    while (timestampIndex >= 0) {
      chars(timestampIndex) = alphabet.charAt((timestamp & 31L).toInt)
      timestamp = timestamp >>> 5
      timestampIndex -= 1
    }

    var outputIndex = 10
    var randomIndex = 0
    var buffer = 0
    var bits = 0
    while (randomIndex < random.length) {
      buffer = (buffer << 8) | (random(randomIndex) & 0xff)
      bits += 8
      while (bits >= 5) {
        bits -= 5
        chars(outputIndex) = alphabet.charAt((buffer >>> bits) & 31)
        outputIndex += 1
      }
      randomIndex += 1
    }

    new String(chars)
  }

  object SysPropNames {
    val RUN_ID = "difflicious.runId"
    val REPORT_OUTPUT_DIR = "difflicious.report.outputDir"
  }
}

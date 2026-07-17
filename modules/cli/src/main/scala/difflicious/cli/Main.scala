package difflicious.cli

import java.io.{InputStream, PrintStream}

object Main {
  def main(args: Array[String]): Unit =
    scala.sys.exit(run(args.toList, System.in, System.out, System.err))

  def run(args: List[String], stdin: InputStream, stdout: PrintStream, stderr: PrintStream): Int =
    run(args, stdin, stdout, stderr, InteractiveReportViewer)

  private[cli] def run(
    args: List[String],
    stdin: InputStream,
    stdout: PrintStream,
    stderr: PrintStream,
    tuiRunner: TuiRunner,
  ): Int =
    CliParser.parse(args) match {
      case Right(CliCommand.ShowHelp) =>
        stdout.println(CliParser.usage)
        0

      case Right(CliCommand.Run(config)) =>
        run(config, stdin, stdout, stderr, tuiRunner)

      case Left(error) =>
        stderr.println(error)
        2
    }

  private def run(
    config: CliConfig,
    stdin: InputStream,
    stdout: PrintStream,
    stderr: PrintStream,
    tuiRunner: TuiRunner,
  ): Int =
    loadReport(config, stdin) match {
      case Left(error) =>
        stderr.println(s"Error: $error")
        2

      case Right(report) =>
        selectReport(config, report) match {
          case Left(error) =>
            stderr.println(s"Error: $error")
            2

          case Right(selectedReport) =>
            render(config, selectedReport, stdout, tuiRunner)
            0
        }
    }

  private def loadReport(config: CliConfig, stdin: InputStream): Either[String, DiffReport] =
    ReporterJsonlReader.read(config.input, stdin, config.mode)

  private def selectReport(config: CliConfig, report: DiffReport): Either[String, SelectedReport] =
    config.testId match {
      case None =>
        Right(SelectedReport(report, initialTuiIndex = 0))

      case Some(testId) if testId.trim.isEmpty =>
        Left("Test id cannot be empty.")

      case Some(testId) =>
        config.mode match {
          case RunMode.Tui =>
            DiffRunSelector.firstMatchingIndex(report, testId) match {
              case Some(index) => Right(SelectedReport(report, initialTuiIndex = index))
              case None => Left(s"No diff failure matched test id '$testId'.")
            }

          case RunMode.NonInteractive(_) =>
            val matches = DiffRunSelector.matchingRuns(report, testId)
            if (matches.nonEmpty) Right(SelectedReport(DiffReport(matches), initialTuiIndex = 0))
            else Left(s"No diff failure matched test id '$testId'.")
        }
    }

  private def render(
    config: CliConfig,
    selectedReport: SelectedReport,
    stdout: PrintStream,
    tuiRunner: TuiRunner,
  ): Unit = {
    val report = selectedReport.report

    config.mode match {
      case RunMode.Tui =>
        tuiRunner.run(report, config.color, selectedReport.initialTuiIndex)

      case RunMode.NonInteractive(OutputFormat.Json) =>
        stdout.println(JsonRenderer.renderReportString(report))

      case RunMode.NonInteractive(OutputFormat.Plain) =>
        stdout.println(PlainRenderer.render(report))
    }
  }

  private final case class SelectedReport(report: DiffReport, initialTuiIndex: Int)
}

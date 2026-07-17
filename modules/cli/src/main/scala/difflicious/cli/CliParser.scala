package difflicious.cli

import cats.syntax.all._
import com.monovore.decline.{Command, Opts}
import difflicious.reporter.DiffliciousResultDefaults

object CliParser {
  lazy val usage: String = withExitCodes(command.showHelp)

  def parse(args: List[String]): Either[String, CliCommand] =
    command.parse(args) match {
      case Right(config) =>
        Right(CliCommand.Run(config))

      case Left(help) if help.errors.isEmpty =>
        Right(CliCommand.ShowHelp)

      case Left(help) =>
        Left(withExitCodes(help.toString))
    }

  private lazy val command: Command[CliConfig] =
    Command(
      name = "difflicious",
      header = "View Difflicious JSON diffs interactively, as JSON, or as plain text.",
      helpFlag = false,
    )(helpOption.orElse(reportConfigOptions))

  private lazy val helpOption: Opts[Nothing] =
    Opts.flag(long = "help", help = "Display this help text.", short = "h").asHelp

  private lazy val reportConfigOptions: Opts[CliConfig] =
    (modeOption, reportInputOptions, testIdOption, noColorOption).mapN { case (mode, input, testId, noColor) =>
      CliConfig(
        mode = mode,
        input = input,
        testId = testId,
        color = !noColor,
      )
    }

  private lazy val modeOption: Opts[RunMode] = {
    val interactive =
      Opts.flag(long = "interactive", help = "Open the interactive terminal viewer.").map(_ => RunMode.Tui).orNone
    val json =
      Opts
        .flag(long = "json", help = "Print machine-readable JSON.")
        .map(_ => RunMode.NonInteractive(OutputFormat.Json))
        .orNone
    val plain =
      Opts
        .flag(long = "plain", help = "Print a plain-text summary.")
        .map(_ => RunMode.NonInteractive(OutputFormat.Plain))
        .orNone

    (interactive, json, plain)
      .mapN((interactive, json, plain) => List(interactive, json, plain).flatten)
      .mapValidated {
        case Nil => RunMode.default.validNel
        case selected :: Nil => selected.validNel
        case _ =>
          "Choose only one output mode: --interactive, --json, or --plain.".invalidNel
      }
  }

  private lazy val reportInputOptions: Opts[CliInput.Report] =
    Opts
      .arguments[String](metavar = "report.jsonl")
      .orNone
      .map {
        case Some(paths) => CliInput.Report(paths.toList.toVector)
        case None => CliInput.Report(Vector(DiffliciousResultDefaults.OutputDirectory))
      }

  private lazy val testIdOption: Opts[Option[String]] =
    Opts
      .option[String](
        long = "test-id",
        help = "Show only the matching reporter ULID in non-interactive modes, or jump to it in TUI mode.",
        metavar = "ulid",
      )
      .orNone

  private lazy val noColorOption: Opts[Boolean] =
    Opts
      .flag(
        long = "no-color",
        help = "Disable ANSI color in TUI output.",
      )
      .orFalse

  private def withExitCodes(help: String): String =
    s"""$help
       |
       |Exit codes:
       |    0
       |        output was rendered successfully, including diff failures
       |    2
       |        invalid arguments, unreadable input, or invalid JSONL""".stripMargin
}

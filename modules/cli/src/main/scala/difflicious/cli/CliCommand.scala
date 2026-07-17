package difflicious.cli

sealed trait CliCommand

object CliCommand {
  case object ShowHelp extends CliCommand
  final case class Run(config: CliConfig) extends CliCommand
}

final case class CliConfig(
  mode: RunMode,
  input: CliInput.Report,
  testId: Option[String],
  color: Boolean,
)

object CliInput {
  final case class Report(paths: Vector[String])
}

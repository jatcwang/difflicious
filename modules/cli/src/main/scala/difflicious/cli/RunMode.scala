package difflicious.cli

sealed trait RunMode

object RunMode {
  case object Tui extends RunMode
  final case class NonInteractive(format: OutputFormat) extends RunMode

  val default: RunMode = Tui

  def parse(value: String): Option[RunMode] = {
    val normalized = value.toLowerCase(java.util.Locale.ROOT)
    if (normalized == "tui" || normalized == "interactive") Some(Tui)
    else OutputFormat.parse(normalized).map(NonInteractive(_))
  }
}

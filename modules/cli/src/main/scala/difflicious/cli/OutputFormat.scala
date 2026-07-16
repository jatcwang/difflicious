package difflicious.cli

sealed trait OutputFormat {
  def name: String
}

object OutputFormat {
  case object Json extends OutputFormat {
    override val name: String = "json"
  }

  case object Plain extends OutputFormat {
    override val name: String = "plain"
  }

  val all: List[OutputFormat] = List(Json, Plain)

  def parse(value: String): Option[OutputFormat] =
    all.find(_.name == value.toLowerCase(java.util.Locale.ROOT))
}

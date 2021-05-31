package difflicious

final case class ConfigurePath(resolvedSteps: Vector[String], unresolvedSteps: List[String]) {
  def next: (Option[String], ConfigurePath) = {
    // FIXME: Will be nicer to have a nicer named alternative than Option
    unresolvedSteps match {
      case head :: tail => Some(head) -> ConfigurePath(resolvedSteps :+ head, tail)
      case Nil          => None -> this
    }
  }
}

object ConfigurePath {
  val current: ConfigurePath = of()

  def fromPath(steps: List[String]): ConfigurePath = {
    ConfigurePath(Vector.empty, steps)
  }

  def of(steps: String*): ConfigurePath = {
    ConfigurePath(Vector.empty, steps.toList)
  }
}

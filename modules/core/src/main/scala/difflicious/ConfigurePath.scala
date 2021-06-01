package difflicious

final case class ConfigurePath(resolvedSteps: Vector[String], unresolvedSteps: List[String])

object ConfigurePath {
  val current: ConfigurePath = of()

  def fromPath(steps: List[String]): ConfigurePath = {
    ConfigurePath(Vector.empty, steps)
  }

  def of(steps: String*): ConfigurePath = {
    ConfigurePath(Vector.empty, steps.toList)
  }
}

package difflicious

final case class UpdatePath(resolvedSteps: Vector[String], unresolvedSteps: List[String]) {
  def next: (Option[String], UpdatePath) = {
    // FIXME: Will be nicer to have a nicer named alternative than Option
    unresolvedSteps match {
      case head :: tail => Some(head) -> UpdatePath(resolvedSteps :+ head, tail)
      case Nil          => None -> this
    }
  }
}

object UpdatePath {
  val current: UpdatePath = of()

  def of(steps: String*): UpdatePath = {
    UpdatePath(Vector.empty, steps.toList)
  }
}

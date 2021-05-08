package difflicious

final case class UpdatePath(resolvedSteps: Vector[UpdateStep], unresolvedSteps: List[UpdateStep]) {
  def next: (Option[UpdateStep], UpdatePath) = {
    // FIXME: Will be nicer to have a nicer named alternative than Option
    unresolvedSteps match {
      case head :: tail => Some(head) -> UpdatePath(resolvedSteps :+ head, tail)
      case Nil          => None -> this
    }
  }
}

object UpdatePath {
  val current: UpdatePath = of()

  def of(steps: UpdateStep*): UpdatePath = {
    UpdatePath(Vector.empty, steps.toList)
  }
}

// FIXME: better name. Or just get rid of this if there's only one subclass
sealed trait UpdateStep

object UpdateStep {
  final case class DownPath(name: String) extends UpdateStep
}

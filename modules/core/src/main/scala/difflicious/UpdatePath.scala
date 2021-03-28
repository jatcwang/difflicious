package difflicious

final case class UpdatePath(resolvedSteps: Vec[UpdateStep], unresolvedSteps: List[UpdateStep]) {
  def next: (Option[UpdateStep], UpdatePath) = {
    unresolvedSteps match {
      case head :: tail => Some(head) -> UpdatePath(resolvedSteps :+ head, tail)
      case Nil          => None -> this
    }
  }
}

object UpdatePath {
  def of(steps: UpdateStep*): UpdatePath = {
    UpdatePath(Vector.empty, steps.toList)
  }
}

// FIXME: better name
sealed trait UpdateStep

object UpdateStep {
  final case class Down(fullTypeName: String) extends UpdateStep
  final case class RecordField(name: String) extends UpdateStep
}

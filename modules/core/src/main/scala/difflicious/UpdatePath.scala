package difflicious

final case class UpdatePath(resolvedSteps: Vec[UpdateStep], unresolvedSteps: List[UpdateStep]) {
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

// FIXME: better name
sealed trait UpdateStep

object UpdateStep {
  // FIXME: Need type param index in the API response as well as subtype name (for ADT sealed traits)?
  // FIXME: Map[String, Either[Double, Option[NonEmptyList[Int]]]]
  final case class DownTypeParam(idx: Int) extends UpdateStep
  final case class DownSubtype(fullSubclassName: String) extends UpdateStep
  final case class RecordField(name: String) extends UpdateStep
}

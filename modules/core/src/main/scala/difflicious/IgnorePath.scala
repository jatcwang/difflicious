package difflicious

final case class IgnorePath(resolvedSteps: Vec[IgnoreStep], unresolvedSteps: List[IgnoreStep]) {
  def next: (Option[IgnoreStep], IgnorePath) = {
    unresolvedSteps match {
      case head :: tail => Some(head) -> IgnorePath(resolvedSteps :+ head, tail)
      case Nil          => None -> this
    }
  }
}

sealed trait IgnoreStep

object IgnoreStep {
  final case class Down(fullTypeName: String) extends IgnoreStep
  final case class RecordField(name: String) extends IgnoreStep
}

package difflicious.cli

object DiffRunSelector {
  def matchingRuns(report: DiffReport, testId: String): Vector[DiffRun] =
    report.runs.filter(matches(_, testId))

  def firstMatchingIndex(report: DiffReport, testId: String): Option[Int] =
    report.runs.indexWhere(matches(_, testId)) match {
      case -1 => None
      case index => Some(index)
    }

  def matches(run: DiffRun, testId: String): Boolean =
    run.metadata.exists(_.testId == testId.trim)
}

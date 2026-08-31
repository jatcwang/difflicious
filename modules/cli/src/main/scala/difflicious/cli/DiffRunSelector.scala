package difflicious.cli

import difflicious.reporter.Ulid

object DiffRunSelector {
  def forTestRunsToShow(report: DiffReport, testRunsToShow: TestRunsToShow): DiffReport =
    testRunsToShow match {
      case TestRunsToShow.AllRuns =>
        report.copy(testRunsToShow = testRunsToShow, selectedRunId = None)
      case TestRunsToShow.SingleRun =>
        latestRunId(report) match {
          case Some(runId) =>
            report.copy(
              runs = report.runs.filter(_.metadata.exists(_.runId == runId)),
              testRunsToShow = testRunsToShow,
              selectedRunId = Some(runId),
            )
          case None =>
            report.copy(testRunsToShow = testRunsToShow, selectedRunId = None)
        }
    }

  def latestRunId(report: DiffReport): Option[String] = {
    val runIds = report.runs.flatMap(_.metadata.map(_.runId)).distinct
    runIds.zipWithIndex
      .maxByOption { case (runId, index) =>
        (Ulid.timestampMillis(runId).getOrElse(Long.MinValue), index)
      }
      .map(_._1)
  }

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

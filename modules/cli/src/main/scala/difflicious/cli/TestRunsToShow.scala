package difflicious.cli

sealed trait TestRunsToShow

object TestRunsToShow {
  case object SingleRun extends TestRunsToShow
  case object AllRuns extends TestRunsToShow

  val default: TestRunsToShow = SingleRun
}

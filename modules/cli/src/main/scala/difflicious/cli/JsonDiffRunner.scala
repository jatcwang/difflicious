package difflicious.cli

import difflicious.DiffResult
import difflicious.circe.CirceInstances
import io.circe.Json

final case class DiffRunMetadata(
  runId: String,
  testId: String,
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  testName: String,
  testText: String,
  testHierarchy: Vector[String],
  fileName: String,
  filePath: String,
  lineNumber: Int,
) {
  def displayName: String =
    (suiteClassName.toVector ++ testHierarchy).mkString(" / ")

  def location: String =
    s"$filePath:$lineNumber"
}

final case class DiffRun(
  result: DiffResult,
  changes: Vector[DiffChange],
  metadata: Option[DiffRunMetadata],
)

object DiffRun {
  def fromResult(result: DiffResult, metadata: Option[DiffRunMetadata]): DiffRun =
    DiffRun(
      result = result,
      changes = DiffResultInspector.collectChanges(result),
      metadata = metadata,
    )
}

final case class DiffReport(runs: Vector[DiffRun]) {
  def isOk: Boolean =
    runs.forall(_.result.isOk)

  def totalChanges: Int =
    runs.map(_.changes.count(change => !change.isIgnored)).sum
}

object JsonDiffRunner {
  def diff(obtained: Json, expected: Json): DiffRun = {
    val result = CirceInstances.jsonDiffer.diff(obtained, expected)
    DiffRun.fromResult(result, metadata = None)
  }
}

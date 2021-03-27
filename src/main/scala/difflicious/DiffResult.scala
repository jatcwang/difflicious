package difflicious

import scala.collection.immutable.ListMap
import io.circe.Json

sealed trait DiffResult {}

object DiffResult {
  // FIXME: add class types
  final case class ListResult(
    matchType: MatchType,
    items: Vec[DiffResult],
  ) extends DiffResult

  final case class SetResult(
    matchType: MatchType,
    items: Vec[DiffResult],
  ) extends DiffResult

  final case class RecordResult(
    matchType: MatchType,
    fields: ListMap[String, DiffResult],
  ) extends DiffResult

  final case class MapResult(
    matchType: MatchType,
    entries: Vec[MapResult.Entry],
  ) extends DiffResult

  object MapResult {
    final case class Entry(key: ValueResult.ActualOnly, value: DiffResult)
  }

  final case class MismatchTypeResult(
    actual: DiffResult,
    expected: DiffResult,
  ) extends DiffResult

  sealed trait ValueResult extends DiffResult

  object ValueResult {
    case class Both(actual: Json, expected: Json, compareResult: CompareResult) extends ValueResult
    case class ActualOnly(actual: Json) extends ValueResult
    case class ExpectedOnly(expected: Json) extends ValueResult
  }
}

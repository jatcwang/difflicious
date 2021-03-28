package difflicious

import scala.collection.immutable.ListMap
import io.circe.Json

sealed trait DiffResult {
  def ignored: Boolean
  def matchType: MatchType
}

object DiffResult {
  // FIXME: add class types
  final case class ListResult(
    items: Vec[DiffResult],
    matchType: MatchType,
    ignored: Boolean,
  ) extends DiffResult

  final case class SetResult(
    items: Vec[DiffResult],
    matchType: MatchType,
    ignored: Boolean,
  ) extends DiffResult

  final case class RecordResult(
    fields: ListMap[String, DiffResult],
    matchType: MatchType,
    ignored: Boolean,
  ) extends DiffResult

  final case class MapResult(
    entries: Vec[MapResult.Entry],
    matchType: MatchType,
    ignored: Boolean,
  ) extends DiffResult

  object MapResult {
    final case class Entry(key: ValueResult.ActualOnly, value: DiffResult)
  }

  final case class MismatchTypeResult(
    actual: DiffResult,
    expected: DiffResult,
    matchType: MatchType,
    ignored: Boolean,
  ) extends DiffResult

  sealed trait ValueResult extends DiffResult

  object ValueResult {
    final case class Both(actual: Json, expected: Json, isIdentical: Boolean, ignored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.Both
    }
    final case class ActualOnly(actual: Json, ignored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.ActualOnly
    }
    final case class ExpectedOnly(expected: Json, ignored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.ExpectedOnly
    }
  }

  def setIgnore(res: DiffResult, ignored: Boolean): DiffResult = {
    res match {
      case r: RecordResult       => r.copy(ignored = ignored)
      case r: MismatchTypeResult => r.copy(ignored = ignored)
      case r: ValueResult =>
        r match {
          case rr: ValueResult.Both         => rr.copy(ignored = ignored)
          case rr: ValueResult.ActualOnly   => rr.copy(ignored = ignored)
          case rr: ValueResult.ExpectedOnly => rr.copy(ignored = ignored)
        }
      case r: ListResult => r.copy(ignored = ignored)
      case r: MapResult  => r.copy(ignored = ignored)
      case r: SetResult  => r.copy(ignored = ignored)
    }

  }
}

package difflicious

import scala.collection.immutable.ListMap
import io.circe.Json

sealed trait DiffResult {
  def isIgnored: Boolean
  def isSame: Boolean
  def matchType: MatchType

  // FIXME: need isOk, probably don't need isSame?
}

object DiffResult {
  // FIXME: add class types
  final case class ListResult(
    items: Vec[DiffResult],
    matchType: MatchType,
    isIgnored: Boolean,
    isSame: Boolean,
  ) extends DiffResult

  final case class SetResult(
    items: Vec[DiffResult],
    matchType: MatchType,
    isIgnored: Boolean,
    isSame: Boolean,
  ) extends DiffResult

  final case class RecordResult(
    fields: ListMap[String, DiffResult],
    matchType: MatchType,
    isIgnored: Boolean,
    isSame: Boolean,
  ) extends DiffResult

  final case class MapResult(
    entries: Vec[MapResult.Entry],
    matchType: MatchType,
    isIgnored: Boolean,
    isSame: Boolean,
  ) extends DiffResult

  object MapResult {
    final case class Entry(key: ValueResult, value: DiffResult)
  }

  final case class MismatchTypeResult(
    actual: DiffResult,
    expected: DiffResult,
    matchType: MatchType,
    isIgnored: Boolean,
  ) extends DiffResult {
    override def isSame: Boolean = false
  }

  sealed trait ValueResult extends DiffResult

  object ValueResult {
    final case class Both(actual: Json, expected: Json, isSame: Boolean, isIgnored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.Both
    }
    final case class ActualOnly(actual: Json, isIgnored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.ActualOnly
      override def isSame: Boolean = false
    }
    final case class ExpectedOnly(expected: Json, isIgnored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.ExpectedOnly
      override def isSame: Boolean = false
    }
  }

}

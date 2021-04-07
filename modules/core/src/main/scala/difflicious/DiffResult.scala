package difflicious

import difflicious.utils.TypeName

import scala.collection.immutable.ListMap
import io.circe.Json

sealed trait DiffResult {
  def isIgnored: Boolean
  def isOk: Boolean
  def matchType: MatchType
}

object DiffResult {
  // FIXME: add class types
  final case class ListResult(
    typeName: TypeName,
    items: Vec[DiffResult],
    matchType: MatchType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  final case class SetResult(
    items: Vec[DiffResult],
    matchType: MatchType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  final case class RecordResult(
    typeName: TypeName,
    fields: ListMap[String, DiffResult],
    matchType: MatchType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  final case class MapResult(
    entries: Vec[MapResult.Entry],
    matchType: MatchType,
    isIgnored: Boolean,
    isOk: Boolean,
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
    override def isOk: Boolean = isIgnored
  }

  sealed trait ValueResult extends DiffResult

  object ValueResult {
    // FIXME: rename isOk to isIdentical for value result
    final case class Both(actual: Json, expected: Json, isOk: Boolean, isIgnored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.Both
    }
    final case class ActualOnly(actual: Json, isIgnored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.ActualOnly
      override def isOk: Boolean = false
    }
    final case class ExpectedOnly(expected: Json, isIgnored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.ExpectedOnly
      override def isOk: Boolean = false
    }
  }

}

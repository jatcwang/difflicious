package difflicious

import difflicious.utils.TypeName

import scala.collection.immutable.ListMap

sealed trait DiffResult {
  def isIgnored: Boolean
  def isOk: Boolean
  def matchType: MatchType
}

object DiffResult {
  final case class ListResult(
    typeName: TypeName,
    items: Vector[DiffResult],
    matchType: MatchType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  final case class SetResult(
    typeName: TypeName,
    items: Vector[DiffResult],
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
    typeName: TypeName,
    entries: Vector[MapResult.Entry],
    matchType: MatchType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  object MapResult {
    final case class Entry(key: String, value: DiffResult)
  }

  final case class MismatchTypeResult(
    obtained: DiffResult,
    obtainedTypeName: TypeName,
    expected: DiffResult,
    expectedTypeName: TypeName,
    matchType: MatchType,
    isIgnored: Boolean,
  ) extends DiffResult {
    override def isOk: Boolean = isIgnored
  }

  sealed trait ValueResult extends DiffResult

  object ValueResult {
    final case class Both(obtained: String, expected: String, isSame: Boolean, isIgnored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.Both
      override def isOk: Boolean = isIgnored || isSame
    }
    final case class ObtainedOnly(obtained: String, isIgnored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.ObtainedOnly
      override def isOk: Boolean = false
    }
    final case class ExpectedOnly(expected: String, isIgnored: Boolean) extends ValueResult {
      override def matchType: MatchType = MatchType.ExpectedOnly
      override def isOk: Boolean = false
    }
  }

}

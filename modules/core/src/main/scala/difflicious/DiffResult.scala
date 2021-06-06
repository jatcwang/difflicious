package difflicious

import difflicious.utils.TypeName

import scala.collection.immutable.ListMap

sealed trait DiffResult {

  /**
    * Whether this DiffResult was produced from an ignored Differ
    * @return
    */
  def isIgnored: Boolean

  /**
    * Whether this DiffResult is consider "successful".
    * If there are any non-ignored differences found, then this should be false
    * @return
    */
  def isOk: Boolean

  /**
    * Whether the input leading to this DiffResult has both sides or just one.
    * @return
    */
  def pairType: PairType
}

object DiffResult {
  final case class ListResult(
    typeName: TypeName,
    items: Vector[DiffResult],
    pairType: PairType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  final case class SetResult(
    typeName: TypeName,
    items: Vector[DiffResult],
    pairType: PairType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  final case class RecordResult(
    typeName: TypeName,
    fields: ListMap[String, DiffResult],
    pairType: PairType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  final case class MapResult(
    typeName: TypeName,
    entries: Vector[MapResult.Entry],
    pairType: PairType,
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
    pairType: PairType,
    isIgnored: Boolean,
  ) extends DiffResult {
    override def isOk: Boolean = isIgnored
  }

  sealed trait ValueResult extends DiffResult

  object ValueResult {
    final case class Both(obtained: String, expected: String, isSame: Boolean, isIgnored: Boolean) extends ValueResult {
      override def pairType: PairType = PairType.Both
      override def isOk: Boolean = isIgnored || isSame
    }
    final case class ObtainedOnly(obtained: String, isIgnored: Boolean) extends ValueResult {
      override def pairType: PairType = PairType.ObtainedOnly
      override def isOk: Boolean = false
    }
    final case class ExpectedOnly(expected: String, isIgnored: Boolean) extends ValueResult {
      override def pairType: PairType = PairType.ExpectedOnly
      override def isOk: Boolean = false
    }
  }

}

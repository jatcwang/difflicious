package difflicious

import difflicious.utils.TypeName.SomeTypeName

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
    typeName: SomeTypeName,
    items: Vector[DiffResult],
    pairType: PairType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  final case class SetResult(
    typeName: SomeTypeName,
    items: Vector[DiffResult],
    pairType: PairType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  final case class RecordResult(
    typeName: SomeTypeName,
    fields: ListMap[String, DiffResult],
    pairType: PairType,
    isIgnored: Boolean,
    isOk: Boolean,
  ) extends DiffResult

  final case class MapResult(
    typeName: SomeTypeName,
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
    obtainedTypeName: SomeTypeName,
    expected: DiffResult,
    expectedTypeName: SomeTypeName,
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

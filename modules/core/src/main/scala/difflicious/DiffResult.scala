package difflicious

import difflicious.utils.TypeName.SomeTypeName

import scala.collection.immutable.ListMap

sealed trait DiffResult {

  /** Whether this DiffResult was produced from an ignored Differ
    * @return
    */
  def isIgnored: Boolean

  /** Whether this DiffResult is consider "successful". If there are any non-ignored differences found, then this should
    * be false
    * @return
    */
  def isOk: Boolean

  /** Whether the input leading to this DiffResult has both sides or just one.
    * @return
    */
  def pairType: PairType

  /** The number of differences found, regardless of if they were ignored or not
    * @return
    */
  def differenceCount: Int

  /** The number of ignored differences
    * @return
    */
  def ignoredCount: Int
}

object DiffResult {
  final case class ListResult(
    typeName: SomeTypeName,
    items: Vector[DiffResult],
    pairType: PairType,
    isIgnored: Boolean,
    isOk: Boolean,
    differenceCount: Int,
    ignoredCount: Int,
  ) extends DiffResult

  final case class RecordResult(
    typeName: SomeTypeName,
    fields: ListMap[String, DiffResult],
    pairType: PairType,
    isIgnored: Boolean,
    isOk: Boolean,
    differenceCount: Int,
    ignoredCount: Int,
  ) extends DiffResult

  final case class MapResult(
    typeName: SomeTypeName,
    entries: Vector[MapResult.Entry],
    pairType: PairType,
    isIgnored: Boolean,
    isOk: Boolean,
    differenceCount: Int,
    ignoredCount: Int,
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
    override def differenceCount: Int = 1
    override def ignoredCount: Int = 1
  }

  sealed trait ValueResult extends DiffResult

  object ValueResult {
    final case class Both(obtained: String, expected: String, isSame: Boolean, isIgnored: Boolean) extends ValueResult {
      override def pairType: PairType = PairType.Both
      override def isOk: Boolean = isIgnored || isSame
      override def differenceCount: Int = if (isSame) 0 else 1
      override def ignoredCount: Int = if (!isSame && isIgnored) 1 else 0
    }
    final case class ObtainedOnly(obtained: String, isIgnored: Boolean) extends ValueResult {
      override def pairType: PairType = PairType.ObtainedOnly
      override def isOk: Boolean = false
      override def differenceCount: Int = 1
      override def ignoredCount: Int = if (isIgnored) 1 else 0
    }
    final case class ExpectedOnly(expected: String, isIgnored: Boolean) extends ValueResult {
      override def pairType: PairType = PairType.ExpectedOnly
      override def isOk: Boolean = false
      override def differenceCount: Int = 1
      override def ignoredCount: Int = if (isIgnored) 1 else 0
    }
  }

}

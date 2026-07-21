package difflicious.cli

import difflicious.PairType

final case class DiffChange(
  path: DiffPath,
  kind: ChangeKind,
  pairType: PairType,
  typeName: String,
  obtained: Option[String],
  expected: Option[String],
  isIgnored: Boolean,
  isOk: Boolean,
  rendered: String,
)

final case class DiffSummary(
  isOk: Boolean,
  totalChanges: Int,
  changed: Int,
  typeMismatches: Int,
  obtainedOnly: Int,
  expectedOnly: Int,
  ignored: Int,
)

object DiffSummary {
  def fromResult(isOk: Boolean, changes: Vector[DiffChange]): DiffSummary =
    DiffSummary(
      isOk = isOk,
      totalChanges = changes.count(change => !change.isIgnored),
      changed = changes.count(_.kind == ChangeKind.Changed),
      typeMismatches = changes.count(_.kind == ChangeKind.TypeMismatch),
      obtainedOnly = changes.count(_.kind == ChangeKind.ObtainedOnly),
      expectedOnly = changes.count(_.kind == ChangeKind.ExpectedOnly),
      ignored = changes.count(_.kind == ChangeKind.Ignored),
    )
}

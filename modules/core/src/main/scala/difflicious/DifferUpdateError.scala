package difflicious

import izumi.reflect.macrortti.LightTypeTag

/** Error type when attempting to ignore a Differ failed, path is invalid
  * (does not match the target differ type) */
sealed trait DifferUpdateError

object DifferUpdateError {
  // FIXME: errors should always contain the current differ type?
  final case class NonExistentField(path: UpdatePath, fieldName: String) extends DifferUpdateError
  final case class PathTooLong(path: UpdatePath) extends DifferUpdateError
  final case class InvalidSubType(path: UpdatePath, allowedTypes: Vec[String]) extends DifferUpdateError
  final case class UnexpectedDifferType(path: UpdatePath, actualDifferType: String) extends DifferUpdateError
  final case class InvalidDifferOp(path: UpdatePath, op: DifferOp, differType: String) extends DifferUpdateError
  final case class MatchByTypeMismatch(path: UpdatePath, actualTag: LightTypeTag, expectedTag: LightTypeTag)
      extends DifferUpdateError
}

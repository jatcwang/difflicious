package difflicious

import izumi.reflect.macrortti.LightTypeTag

/** Error type when attempting to ignore a Differ failed, path is invalid
  * (does not match the target differ type) */
sealed trait DifferUpdateError

object DifferUpdateError {
  // FIXME: errors should always contain the current differ type?
  final case class NonExistentField(path: UpdatePath, fieldName: String) extends DifferUpdateError
  final case class PathTooLong(path: UpdatePath) extends DifferUpdateError
  final case class InvalidSubType(path: UpdatePath, allowedTypes: Vector[String]) extends DifferUpdateError
  // FIXME: align use of expectedDifferType param
  final case class UnexpectedDifferType(path: UpdatePath, expectedDifferType: String) extends DifferUpdateError
  // FIXME: align use of differType param
  final case class InvalidDifferOp(path: UpdatePath, op: DifferOp, differType: String) extends DifferUpdateError
  final case class MatchByTypeMismatch(path: UpdatePath, obtainedTag: LightTypeTag, expectedTag: LightTypeTag)
      extends DifferUpdateError
}

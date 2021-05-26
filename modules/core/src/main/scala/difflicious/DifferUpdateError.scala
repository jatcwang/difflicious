package difflicious

import izumi.reflect.macrortti.LightTypeTag

/** Error type when attempting to ignore a Differ failed, path is invalid
  * (does not match the target differ type) */
sealed trait DifferUpdateError

object DifferUpdateError {
  // FIXME: errors should always contain the current differ type?
  final case class NonExistentField(path: ConfigurePath, fieldName: String) extends DifferUpdateError
  final case class PathTooLong(path: ConfigurePath) extends DifferUpdateError
  final case class InvalidSubType(path: ConfigurePath, allowedTypes: Vector[String]) extends DifferUpdateError
  // FIXME: align use of differType param
  final case class InvalidDifferOp(path: ConfigurePath, op: ConfigureOp, differType: String) extends DifferUpdateError
  final case class PairByTypeMismatch(path: ConfigurePath, obtainedTag: LightTypeTag, expectedTag: LightTypeTag)
      extends DifferUpdateError
}

package difflicious

/** Error type when attempting to ignore a Differ failed, path is invalid
  * (does not match the target differ type) */
sealed trait IgnoreError

object IgnoreError {
  final case class NonExistentField(path: Vec[IgnoreStep], fieldName: String) extends IgnoreError
  final case class PathTooLong(path: Vec[IgnoreStep]) extends IgnoreError
  final case class InvalidSubType(path: Vec[IgnoreStep], allowedTypes: Vec[String]) extends IgnoreError
  final case class UnexpectedDifferType(path: Vec[IgnoreStep], actualDifferType: String) extends IgnoreError
}

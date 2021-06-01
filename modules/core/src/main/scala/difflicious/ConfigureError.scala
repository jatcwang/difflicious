package difflicious

import izumi.reflect.macrortti.LightTypeTag

/** Error type when attempting to ignore a Differ failed, path is invalid
  * (does not match the target differ type) */
sealed trait ConfigureError extends Throwable {
  def errorMessage: String
  override def getMessage: String = errorMessage
}

object ConfigureError {
  private def resolvedPath(path: ConfigurePath): String = path.resolvedSteps.mkString(".")

  private def unresolvedPath(path: ConfigurePath): String = path.unresolvedSteps.mkString(".")

  // FIXME: errors should always contain the current differ type?
  final case class NonExistentField(path: ConfigurePath, fieldName: String) extends ConfigureError {
    override def errorMessage: String = s"Field does not exist at path ${resolvedPath(path)}"
  }
  final case class PathTooLong(path: ConfigurePath) extends ConfigureError {
    override def errorMessage: String =
      s"Configure path is too long. Reached a 'leaf' Differ while there are still unresolved steps. " +
        s"Current path: ${resolvedPath(path)}, Leftover steps: ${unresolvedPath(path)}"
  }
  final case class UnrecognizedSubType(path: ConfigurePath, allowedTypes: Vector[String]) extends ConfigureError {
    override def errorMessage: String =
      s"Unrecognized subtype at path ${resolvedPath(path)}. Known types are ${allowedTypes.mkString(",")}"
  }
  // FIXME: align use of differType param
  final case class InvalidConfigureOp(path: ConfigurePath, op: ConfigureOp, differType: String) extends ConfigureError {
    override def errorMessage: String =
      s"The differ you're trying to configure (${differType}) does now allow the provided ConfigureOp ${op}" +
        s"Current path: ${resolvedPath(path)}"
  }
  final case class PairByTypeMismatch(path: ConfigurePath, obtainedTag: LightTypeTag, expectedTag: LightTypeTag)
      extends ConfigureError {
    override def errorMessage: String =
      s"Type mismatch when trying to configure pair by at path: ${resolvedPath(path)}. " +
        s"Expected ${expectedTag} but got ${obtainedTag}"
  }
}

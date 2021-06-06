package difflicious

import izumi.reflect.macrortti.LightTypeTag

sealed trait ConfigureError extends Throwable {
  def errorMessage: String
  override def getMessage: String = errorMessage
}

object ConfigureError {
  private def resolvedPath(path: ConfigurePath): String = path.resolvedSteps.mkString(".")

  private def unresolvedPath(path: ConfigurePath): String = path.unresolvedSteps.mkString(".")

  final case class NonExistentField(path: ConfigurePath, dfferType: String) extends ConfigureError {
    override def errorMessage: String = s"Field does not exist at path ${resolvedPath(path)}"
  }
  final case class PathTooLong(path: ConfigurePath) extends ConfigureError {
    override def errorMessage: String =
      s"Configure path is too long. Reached a 'leaf' Differ while there are still unresolved steps. " +
        s"Current path: ${resolvedPath(path)}, Leftover steps: ${unresolvedPath(path)}"
  }
  final case class PathTooShortForReplace() extends ConfigureError {
    override def errorMessage: String = "Path cannot be empty for Replace command"
  }
  final case class UnrecognizedSubType(path: ConfigurePath, allowedTypes: Vector[String]) extends ConfigureError {
    override def errorMessage: String =
      s"Unrecognized subtype at path ${resolvedPath(path)}. Known types are ${allowedTypes.mkString(",")}"
  }
  final case class InvalidConfigureOp(path: ConfigurePath, op: ConfigureOp, differType: String) extends ConfigureError {
    override def errorMessage: String =
      s"The differ you're trying to configure (${differType}) does now allow the provided ConfigureOp ${op}" +
        s"Current path: ${resolvedPath(path)}"
  }
  final case class TypeTagMismatch(path: ConfigurePath, obtainedTag: LightTypeTag, expectedTag: LightTypeTag)
      extends ConfigureError {
    override def errorMessage: String =
      s"The new differ's type tag is different from the to-be-replaced differ's type tag. At path: ${resolvedPath(path)}. " +
        s"Expected ${expectedTag} but got ${obtainedTag}"
  }
}

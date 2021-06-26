package difflicious.differ

import difflicious.{DiffResult, ConfigureOp, ConfigureError, ConfigurePath, DiffInput}

final class NumericDiffer[T](isIgnored: Boolean, numeric: Numeric[T]) extends ValueDiffer[T] {
  @inline
  private def valueToString(t: T) = t.toString

  override def diff(inputs: DiffInput[T]): DiffResult.ValueResult = inputs match {
    case DiffInput.Both(obtained, expected) => {
      DiffResult.ValueResult.Both(
        valueToString(obtained),
        valueToString(expected),
        isSame = numeric.equiv(obtained, expected),
        isIgnored = isIgnored,
      )
    }
    case DiffInput.ObtainedOnly(obtained) =>
      DiffResult.ValueResult.ObtainedOnly(valueToString(obtained), isIgnored = isIgnored)
    case DiffInput.ExpectedOnly(expected) =>
      DiffResult.ValueResult.ExpectedOnly(valueToString(expected), isIgnored = isIgnored)
  }

  override def configureIgnored(newIgnored: Boolean): NumericDiffer[T] =
    new NumericDiffer[T](isIgnored = newIgnored, numeric = numeric)

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, NumericDiffer[T]] = Left(ConfigureError.PathTooLong(nextPath))

  override def configurePairBy(
    path: ConfigurePath,
    op: ConfigureOp.PairBy[_],
  ): Either[ConfigureError, NumericDiffer[T]] =
    Left(ConfigureError.InvalidConfigureOp(path, op, "NumericDiffer"))

}

object NumericDiffer {
  def make[T](implicit numeric: Numeric[T]): NumericDiffer[T] =
    new NumericDiffer[T](isIgnored = false, numeric = numeric)
}

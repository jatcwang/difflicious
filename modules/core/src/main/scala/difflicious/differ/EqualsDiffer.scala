package difflicious.differ

import difflicious.ConfigureOp.PairBy
import difflicious.{ConfigureError, ConfigureOp, ConfigurePath, DiffInput, DiffResult}

/**
  * Differ where the two values are compared by using the equals method.
  * If the two values aren't equal, then we use the provided valueToString function
  * to output the diagnostic output.
  */
final class EqualsDiffer[T](isIgnored: Boolean, valueToString: T => String) extends ValueDiffer[T] {
  override def diff(inputs: DiffInput[T]): DiffResult.ValueResult = inputs match {
    case DiffInput.Both(obtained, expected) =>
      DiffResult.ValueResult
        .Both(
          obtained = valueToString(obtained),
          expected = valueToString(expected),
          isSame = obtained == expected,
          isIgnored = isIgnored,
        )
    case DiffInput.ObtainedOnly(obtained) =>
      DiffResult.ValueResult.ObtainedOnly(valueToString(obtained), isIgnored = isIgnored)
    case DiffInput.ExpectedOnly(expected) =>
      DiffResult.ValueResult.ExpectedOnly(valueToString(expected), isIgnored = isIgnored)
  }

  override def configureIgnored(newIgnored: Boolean): EqualsDiffer[T] =
    new EqualsDiffer[T](isIgnored = newIgnored, valueToString = valueToString)

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, EqualsDiffer[T]] = Left(ConfigureError.PathTooLong(nextPath))

  override def configurePairBy(path: ConfigurePath, op: PairBy[_]): Either[ConfigureError, EqualsDiffer[T]] =
    Left(ConfigureError.InvalidConfigureOp(path, op, "EqualsDiffer"))

}

package difflicious.differ

import difflicious.ConfigureOp.PairBy
import difflicious.{ConfigureError, ConfigureOp, ConfigurePath, DiffInput, DiffResult}
import difflicious.utils.TypeName

/** Differ where the two values are compared by using the equals method. If the two values aren't equal, then we use the
  * provided valueToString function to output the diagnostic output.
  */
final class EqualsDiffer[T](
  isIgnored: Boolean,
  valueToString: T => String,
  typeName: TypeName[T],
  override val canUseEquals: Boolean,
) extends ValueDiffer[T] {
  override def diff(inputs: DiffInput[T]): DiffResult.ValueResult = inputs match {
    case DiffInput.Both(obtained, expected) =>
      DiffResult.ValueResult
        .Both(
          typeName = typeName,
          obtained = valueToString(obtained),
          expected = valueToString(expected),
          isSame = obtained == expected,
          isIgnored = isIgnored,
        )
    case DiffInput.ObtainedOnly(obtained) =>
      DiffResult.ValueResult.ObtainedOnly(typeName, valueToString(obtained), isIgnored = isIgnored)
    case DiffInput.ExpectedOnly(expected) =>
      DiffResult.ValueResult.ExpectedOnly(typeName, valueToString(expected), isIgnored = isIgnored)
  }

  override def configureIgnored(newIgnored: Boolean): EqualsDiffer[T] =
    new EqualsDiffer[T](
      isIgnored = newIgnored,
      valueToString = valueToString,
      typeName = typeName,
      canUseEquals = false,
    )

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, EqualsDiffer[T]] = Left(ConfigureError.PathTooLong(nextPath))

  override def configurePairBy(path: ConfigurePath, op: PairBy[?]): Either[ConfigureError, EqualsDiffer[T]] =
    Left(ConfigureError.InvalidConfigureOp(path, op, "EqualsDiffer"))

}

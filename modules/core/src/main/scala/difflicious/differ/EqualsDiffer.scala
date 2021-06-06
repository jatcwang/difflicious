package difflicious.differ

import difflicious.Differ.Typeclass
import difflicious.ConfigureOp.PairBy
import difflicious.{DiffResult, ConfigureOp, ConfigureError, ConfigurePath, DiffInput}
import izumi.reflect.macrortti.LTag

final class EqualsDiffer[T](isIgnored: Boolean, valueToString: T => String, override protected val tag: LTag[T])
    extends ValueDiffer[T] {
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

  override def configureIgnored(newIgnored: Boolean): Typeclass[T] =
    new EqualsDiffer[T](isIgnored = newIgnored, valueToString = valueToString, tag = tag)

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Typeclass[T]] = Left(ConfigureError.PathTooLong(nextPath))

  override def configurePairBy(path: ConfigurePath, op: PairBy[_]): Either[ConfigureError, Typeclass[T]] =
    Left(ConfigureError.InvalidConfigureOp(path, op, "EqualsDiffer"))

}

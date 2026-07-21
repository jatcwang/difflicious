package difflicious.differ

import difflicious.{DiffResult, ConfigureOp, ConfigureError, ConfigurePath, DiffInput}
import difflicious.utils.TypeName

final class NumericDiffer[T](isIgnored: Boolean, numeric: Numeric[T], valueToString: T => String, typeName: TypeName[T])
    extends ValueDiffer[T] {

  override def diff(inputs: DiffInput[T]): DiffResult.ValueResult = inputs match {
    case DiffInput.Both(obtained, expected) => {
      DiffResult.ValueResult.Both(
        typeName,
        valueToString(obtained),
        valueToString(expected),
        isSame = numeric.equiv(obtained, expected),
        isIgnored = isIgnored,
      )
    }
    case DiffInput.ObtainedOnly(obtained) =>
      DiffResult.ValueResult.ObtainedOnly(typeName, valueToString(obtained), isIgnored = isIgnored)
    case DiffInput.ExpectedOnly(expected) =>
      DiffResult.ValueResult.ExpectedOnly(typeName, valueToString(expected), isIgnored = isIgnored)
  }

  override def configureIgnored(newIgnored: Boolean): NumericDiffer[T] =
    new NumericDiffer[T](isIgnored = newIgnored, numeric = numeric, valueToString = valueToString, typeName = typeName)

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, NumericDiffer[T]] = Left(ConfigureError.PathTooLong(nextPath))

  override def configurePairBy(
    path: ConfigurePath,
    op: ConfigureOp.PairBy[?],
  ): Either[ConfigureError, NumericDiffer[T]] =
    Left(ConfigureError.InvalidConfigureOp(path, op, "NumericDiffer"))

}

object NumericDiffer {
  def make[T](valueToString: T => String, typeName: TypeName[T])(implicit numeric: Numeric[T]): NumericDiffer[T] =
    new NumericDiffer[T](isIgnored = false, numeric = numeric, valueToString = valueToString, typeName = typeName)
}

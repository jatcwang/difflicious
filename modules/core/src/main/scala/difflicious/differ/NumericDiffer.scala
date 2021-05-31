package difflicious.differ

import difflicious.{DiffResult, ConfigureOp, DiffInput, ConfigurePath, ConfigureError}
import izumi.reflect.Tag
import difflicious.Differ.ValueDiffer

final class NumericDiffer[T](isIgnored: Boolean, numeric: Numeric[T], tag: Tag[T]) extends ValueDiffer[T] {
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

  override def configureRaw(path: ConfigurePath, op: ConfigureOp): Either[ConfigureError, NumericDiffer[T]] = {
    val (step, nextPath) = path.next
    (step, op) match {
      case (Some(_), _) => Left(ConfigureError.PathTooLong(nextPath))
      case (None, ConfigureOp.SetIgnored(newIgnored)) =>
        Right(new NumericDiffer[T](isIgnored = newIgnored, numeric = numeric, tag = tag))
      case (None, otherOp) =>
        Left(ConfigureError.InvalidDifferOp(nextPath, otherOp, "NumericDiffer"))
    }
  }
}

object NumericDiffer {
  def make[T](implicit numeric: Numeric[T], tag: Tag[T]): NumericDiffer[T] =
    new NumericDiffer[T](isIgnored = false, numeric = numeric, tag = tag)
}

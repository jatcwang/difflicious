package difflicious.differ

import cats.data.Ior
import difflicious.{UpdatePath, DifferUpdateError, DifferOp, DiffResult}
import izumi.reflect.Tag
import difflicious.Differ.ValueDiffer

final class NumericDiffer[T](isIgnored: Boolean, numeric: Numeric[T], tag: Tag[T]) extends ValueDiffer[T] {
  @inline
  private def valueToString(t: T) = t.toString

  override def diff(inputs: Ior[T, T]): DiffResult.ValueResult = inputs match {
    case Ior.Both(obtained, expected) => {
      DiffResult.ValueResult.Both(
        valueToString(obtained),
        valueToString(expected),
        isSame = numeric.equiv(obtained, expected),
        isIgnored = isIgnored,
      )
    }
    case Ior.Left(obtained)  => DiffResult.ValueResult.ObtainedOnly(valueToString(obtained), isIgnored = isIgnored)
    case Ior.Right(expected) => DiffResult.ValueResult.ExpectedOnly(valueToString(expected), isIgnored = isIgnored)
  }

  override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, NumericDiffer[T]] = {
    val (step, nextPath) = path.next
    (step, op) match {
      case (Some(_), _) => Left(DifferUpdateError.PathTooLong(nextPath))
      case (None, DifferOp.SetIgnored(newIgnored)) =>
        Right(new NumericDiffer[T](isIgnored = newIgnored, numeric = numeric, tag = tag))
      case (None, otherOp) =>
        Left(DifferUpdateError.InvalidDifferOp(nextPath, otherOp, "NumericDiffer"))
    }
  }
}

object NumericDiffer {
  def make[T](implicit numeric: Numeric[T], tag: Tag[T]): NumericDiffer[T] =
    new NumericDiffer[T](isIgnored = false, numeric = numeric, tag = tag)
}

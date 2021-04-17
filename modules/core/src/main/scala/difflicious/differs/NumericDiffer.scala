package difflicious.differs

import io.circe.Encoder
import cats.data.Ior
import difflicious.{UpdatePath, DifferUpdateError, DifferOp, DiffResult}
import izumi.reflect.Tag
import difflicious.Differ.ValueDiffer

final class NumericDiffer[T](isIgnored: Boolean, numeric: Numeric[T], encoder: Encoder[T], tag: Tag[T])
    extends ValueDiffer[T] {
  override def diff(inputs: Ior[T, T]): DiffResult.ValueResult = inputs match {
    case Ior.Both(actual, expected) => {
      DiffResult.ValueResult.Both(
        encoder.apply(actual),
        encoder.apply(expected),
        isSame = numeric.equiv(actual, expected),
        isIgnored = isIgnored,
      )
    }
    case Ior.Left(actual)    => DiffResult.ValueResult.ActualOnly(encoder.apply(actual), isIgnored = isIgnored)
    case Ior.Right(expected) => DiffResult.ValueResult.ExpectedOnly(encoder.apply(expected), isIgnored = isIgnored)
  }

  override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, NumericDiffer[T]] = {
    val (step, nextPath) = path.next
    (step, op) match {
      case (Some(_), _) => Left(DifferUpdateError.PathTooLong(nextPath))
      case (None, DifferOp.SetIgnored(newIgnored)) =>
        Right(new NumericDiffer[T](isIgnored = newIgnored, numeric = numeric, encoder = encoder, tag = tag))
      case (None, otherOp) =>
        Left(DifferUpdateError.InvalidDifferOp(nextPath, otherOp, "NumericDiffer"))
    }
  }
}

object NumericDiffer {
  def make[T](implicit numeric: Numeric[T], encoder: Encoder[T], tag: Tag[T]): NumericDiffer[T] =
    new NumericDiffer[T](isIgnored = false, numeric = numeric, encoder = encoder, tag = tag)
}

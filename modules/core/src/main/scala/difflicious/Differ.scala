package difflicious
import io.circe.Encoder
import cats.data.Ior

import scala.collection.immutable.ListMap

// FIXME: don't use cats Ior
trait Differ[T] {
  def diff(inputs: Ior[T, T]): DiffResult

  final def diff(actual: T, expected: T): DiffResult = diff(Ior.Both(actual, expected))

  /**
    * Create an new Differ instance where the given path will produce an ignored DiffResult
    */
  def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, Differ[T]]
}

sealed trait DifferOp

object DifferOp {
  final case class SetIgnored(isIgnored: Boolean) extends DifferOp
  sealed trait MatchBy extends DifferOp
  object MatchBy {
    case object Index extends MatchBy
    case class FieldValue(fieldName: String) extends MatchBy
  }

}

object UpdateDiffer {}

object Differ {
  trait ValueDiffer[T] extends Differ[T] {
    override def diff(inputs: Ior[T, T]): DiffResult.ValueResult
  }

  final class EqualsDiffer[T](ignored: Boolean)(implicit encoder: Encoder[T]) extends ValueDiffer[T] {
    override def diff(inputs: Ior[T, T]): DiffResult.ValueResult = inputs match {
      case Ior.Both(actual, expected) =>
        DiffResult.ValueResult
          .Both(
            actual = encoder.apply(actual),
            expected = encoder.apply(expected),
            isSame = actual == expected,
            isIgnored = ignored,
          )
      case Ior.Left(actual) =>
        DiffResult.ValueResult.ActualOnly(encoder.apply(actual), isIgnored = ignored)
      case Ior.Right(expected) =>
        DiffResult.ValueResult.ExpectedOnly(encoder.apply(expected), isIgnored = ignored)
    }

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, EqualsDiffer[T]] = {
      val (step, nextPath) = path.next
      (step, op) match {
        case (Some(_), _)                            => Left(DifferUpdateError.PathTooLong(nextPath))
        case (None, DifferOp.SetIgnored(newIgnored)) => Right(new EqualsDiffer[T](ignored = newIgnored))
        case (None, otherOp)                         => Left(DifferUpdateError.InvalidDifferOp(nextPath, otherOp, "EqualsDiffer"))
      }
    }
  }

  def useEquals[T: Encoder]: ValueDiffer[T] = new EqualsDiffer[T](ignored = false)

  implicit val stringDiff: ValueDiffer[String] = useEquals[String]
  implicit val charDiff: ValueDiffer[Char] = useEquals[Char]
  implicit val booleanDiff: ValueDiffer[Boolean] = useEquals[Boolean]

  class NumericDiffer[T](isIgnored: Boolean)(implicit numeric: Numeric[T], encoder: Encoder[T]) extends ValueDiffer[T] {
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
        case (Some(_), _)                            => Left(DifferUpdateError.PathTooLong(nextPath))
        case (None, DifferOp.SetIgnored(newIgnored)) => Right(new NumericDiffer[T](isIgnored = newIgnored))
        case (None, otherOp) =>
          Left(DifferUpdateError.InvalidDifferOp(nextPath, otherOp, "NumericDiffer"))
      }
    }
  }

  implicit def numericDiff[T](implicit numeric: Numeric[T], encoder: Encoder[T]): ValueDiffer[T] =
    new NumericDiffer[T](isIgnored = false)

  class RecordDiffer[T](
    fieldDiffers: ListMap[String, (T => Any, Differ[Any])],
    ignored: Boolean,
  ) extends Differ[T] {
    override def diff(inputs: Ior[T, T]): DiffResult = inputs match {
      case Ior.Both(actual, expected) => {
        val diffResults = fieldDiffers
          .map {
            case (fieldName, (getter, differ)) =>
              val actualValue = getter(actual)
              val expectedValue = getter(expected)
              val diffResult = differ.diff(actualValue, expectedValue)

              fieldName -> diffResult
          }
          .to(ListMap)
        DiffResult
          .RecordResult(diffResults, MatchType.Both, isIgnored = ignored, isSame = diffResults.values.forall(_.isSame))
      }
      case Ior.Left(value) => {
        val diffResults = fieldDiffers
          .map {
            case (fieldName, (getter, differ)) =>
              val fieldValue = getter(value)
              val diffResult = differ.diff(Ior.left(fieldValue))

              fieldName -> diffResult
          }
          .to(ListMap)
        DiffResult
          .RecordResult(diffResults, MatchType.ActualOnly, isIgnored = ignored, diffResults.values.forall(_.isSame))
      }
      case Ior.Right(expected) => {
        val diffResults = fieldDiffers
          .map {
            case (fieldName, (getter, differ)) =>
              val fieldValue = getter(expected)
              val diffResult = differ.diff(Ior.Right(fieldValue))

              fieldName -> diffResult
          }
          .to(ListMap)
        DiffResult
          .RecordResult(diffResults, MatchType.ExpectedOnly, isIgnored = ignored, diffResults.values.forall(_.isSame))
      }
    }

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, RecordDiffer[T]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(UpdateStep.RecordField(fieldName)) =>
          for {
            (getter, fieldDiffer) <- fieldDiffers
              .get(fieldName)
              .toRight(DifferUpdateError.NonExistentField(nextPath, fieldName))
            newFieldDiffer <- fieldDiffer.updateWith(nextPath, op)
          } yield new RecordDiffer[T](
            ignored = this.ignored,
            fieldDiffers = fieldDiffers.updated(fieldName, (getter, newFieldDiffer)),
          )
        case Some(_) => Left(DifferUpdateError.UnexpectedDifferType(nextPath, "record"))
        case None =>
          op match {
            case DifferOp.SetIgnored(newIgnored) =>
              Right(new RecordDiffer[T](ignored = newIgnored, fieldDiffers = fieldDiffers))
            case _: DifferOp.MatchBy => Left(DifferUpdateError.InvalidDifferOp(nextPath, op, "record"))
          }

      }
    }

    def unsafeIgnoreField(fieldName: String): RecordDiffer[T] =
      updateWith(UpdatePath(Vector.empty, UpdateStep.RecordField(fieldName) :: Nil), DifferOp.SetIgnored(true)) match {
        case Left(_) =>
          throw new IllegalArgumentException(s"Cannot ignore field: field '$fieldName' is not part of record")
        case Right(differ) => differ
      }
  }
}

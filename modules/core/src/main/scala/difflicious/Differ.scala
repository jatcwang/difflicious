package difflicious
import io.circe.Encoder
import cats.data.Ior
import difflicious.DiffResult.MapResult

import scala.collection.immutable.ListMap
import scala.collection.mutable

// FIXME: don't use cats Ior
trait Differ[T] {
  type R <: DiffResult

  def diff(inputs: Ior[T, T]): R

  final def diff(actual: T, expected: T): R = diff(Ior.Both(actual, expected))

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
    final override type R = DiffResult.ValueResult

    override def diff(inputs: Ior[T, T]): R
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

  final class RecordDiffer[T](
    fieldDiffers: ListMap[String, (T => Any, Differ[Any])],
    ignored: Boolean,
  ) extends Differ[T] {
    override type R = DiffResult.RecordResult

    override def diff(inputs: Ior[T, T]): R = inputs match {
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
      updateWith(UpdatePath.of(UpdateStep.RecordField(fieldName)), DifferOp.SetIgnored(true)) match {
        case Left(_) =>
          throw new IllegalArgumentException(s"Cannot ignore field: field '$fieldName' is not part of record")
        case Right(differ) => differ
      }
  }

  implicit def mapDiffer[A: ValueDiffer, B: Differ, M[KK, VV] <: Map[KK, VV]]: MapDiffer[A, B, M] =
    new MapDiffer(isIgnored = true)

  // FIXME: probably want some sort of ordering to maintain consistent order
  final class MapDiffer[A, B, M[KK, VV] <: Map[KK, VV]](
    isIgnored: Boolean,
  )(
    implicit
    keyDiffer: ValueDiffer[A],
    valueDiffer: Differ[B],
  ) extends Differ[M[A, B]] {
    override type R = MapResult

    override def diff(inputs: Ior[M[A, B], M[A, B]]): R = inputs match {
      case Ior.Both(actual, expected) =>
        val actualOnly = mutable.ArrayBuffer.empty[MapResult.Entry]
        val both = mutable.ArrayBuffer.empty[MapResult.Entry]
        val expectedOnly = mutable.ArrayBuffer.empty[MapResult.Entry]
        actual.foreach {
          case (k, actualV) =>
            expected.get(k) match {
              case Some(expectedV) =>
                both += MapResult.Entry(
                  keyDiffer.diff(k, k),
                  valueDiffer.diff(actualV, expectedV),
                )
              case None =>
                actualOnly += MapResult.Entry(
                  keyDiffer.diff(Ior.Left(k)),
                  valueDiffer.diff(Ior.Left(actualV)),
                )
            }
        }
        expected.foreach {
          case (k, expectedV) =>
            if (actual.contains(k)) {
              // Do nothing, already compared when iterating through actual
            } else {
              expectedOnly += MapResult.Entry(
                keyDiffer.diff(Ior.Right(k)),
                valueDiffer.diff(Ior.Right(expectedV)),
              )
            }
        }
        (actualOnly ++ both ++ expectedOnly).toVector
        MapResult(
          (actualOnly ++ both ++ expectedOnly).toVector,
          MatchType.Both,
          isIgnored = isIgnored,
          isSame = actualOnly.isEmpty && expectedOnly.isEmpty && both.forall(_.value.isSame),
        )
      case Ior.Left(actual) =>
        DiffResult.MapResult(
          entries = actual.map {
            case (k, v) =>
              MapResult.Entry(keyDiffer.diff(Ior.Left(k)), valueDiffer.diff(Ior.Left(v)))
          }.toVector,
          matchType = MatchType.ActualOnly,
          isIgnored = isIgnored,
          isSame = false,
        )
      case Ior.Right(expected) =>
        DiffResult.MapResult(
          entries = expected.map {
            case (k, v) =>
              MapResult.Entry(keyDiffer.diff(Ior.Right(k)), valueDiffer.diff(Ior.Right(v)))
          }.toVector,
          matchType = MatchType.ActualOnly,
          isIgnored = isIgnored,
          isSame = false,
        )
    }

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, Differ[M[A, B]]] = ???
  }
}

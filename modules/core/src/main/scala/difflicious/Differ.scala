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
  def updateIgnore(path: IgnorePath, newIgnored: Boolean): Either[IgnoreError, Differ[T]]
}

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
            isIdentical = actual == expected,
            ignored = ignored,
          )
      case Ior.Left(actual) =>
        DiffResult.ValueResult.ActualOnly(encoder.apply(actual), ignored = ignored)
      case Ior.Right(expected) =>
        DiffResult.ValueResult.ExpectedOnly(encoder.apply(expected), ignored = ignored)
    }

    override def updateIgnore(path: IgnorePath, newIgnored: Boolean): Either[IgnoreError, EqualsDiffer[T]] = {
      path.next match {
        case (Some(_), nextPath) => Left(IgnoreError.PathTooLong(nextPath.resolvedSteps))
        case (None, _)           => Right(new EqualsDiffer[T](ignored = newIgnored))
      }
    }
  }

  def useEquals[T: Encoder]: ValueDiffer[T] = new EqualsDiffer[T](ignored = false)

  implicit val stringDiff: ValueDiffer[String] = useEquals[String]
  implicit val charDiff: ValueDiffer[Char] = useEquals[Char]
  implicit val booleanDiff: ValueDiffer[Boolean] = useEquals[Boolean]

  class NumericDiffer[T](ignored: Boolean)(implicit numeric: Numeric[T], encoder: Encoder[T]) extends ValueDiffer[T] {
    override def diff(inputs: Ior[T, T]): DiffResult.ValueResult = inputs match {
      case Ior.Both(actual, expected) => {
        DiffResult.ValueResult.Both(
          encoder.apply(actual),
          encoder.apply(expected),
          isIdentical = numeric.equiv(actual, expected),
          ignored = ignored,
        )
      }
      case Ior.Left(actual)    => DiffResult.ValueResult.ActualOnly(encoder.apply(actual), ignored = ignored)
      case Ior.Right(expected) => DiffResult.ValueResult.ExpectedOnly(encoder.apply(expected), ignored = ignored)
    }

    override def updateIgnore(path: IgnorePath, newIgnored: Boolean): Either[IgnoreError, NumericDiffer[T]] = {
      path.next match {
        case (Some(_), nextPath) => Left(IgnoreError.PathTooLong(nextPath.resolvedSteps))
        case (None, _)           => Right(new NumericDiffer[T](ignored = newIgnored))
      }
    }
  }

  implicit def numericDiff[T](implicit numeric: Numeric[T], encoder: Encoder[T]): ValueDiffer[T] =
    new NumericDiffer[T](ignored = false)

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
        DiffResult.RecordResult(diffResults, MatchType.Both, ignored = ignored)
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
        DiffResult.RecordResult(diffResults, MatchType.ActualOnly, ignored = ignored)
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
        DiffResult.RecordResult(diffResults, MatchType.ExpectedOnly, ignored = ignored)
      }
    }

    override def updateIgnore(path: IgnorePath, newIgnored: Boolean): Either[IgnoreError, RecordDiffer[T]] = {
      path.next match {
        case (Some(IgnoreStep.RecordField(fieldName)), nextPath) => {
          for {
            (getter, fieldDiffer) <- fieldDiffers
              .get(fieldName)
              .toRight(IgnoreError.NonExistentField(nextPath.resolvedSteps, fieldName))
            newFieldDiffer <- fieldDiffer.updateIgnore(nextPath, newIgnored)
          } yield new RecordDiffer[T](
            ignored = this.ignored,
            fieldDiffers = fieldDiffers.updated(fieldName, (getter, newFieldDiffer)),
          )
        }
        case (Some(_), nextPath) => Left(IgnoreError.UnexpectedDifferType(nextPath.resolvedSteps, "record"))
        case (None, _)           => Right(new RecordDiffer[T](ignored = newIgnored, fieldDiffers = fieldDiffers))
      }
    }

    def unsafeIgnoreField(fieldName: String): RecordDiffer[T] =
      updateIgnore(IgnorePath(Vector.empty, IgnoreStep.RecordField(fieldName) :: Nil), newIgnored = true) match {
        case Left(_) =>
          throw new IllegalArgumentException(s"Cannot ignore field: field '$fieldName' is not part of record")
        case Right(differ) => differ
      }
  }
}

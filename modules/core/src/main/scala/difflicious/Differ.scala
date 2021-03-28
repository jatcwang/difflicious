package difflicious
import io.circe.Encoder
import cats.data.Ior

import scala.collection.immutable.ListMap

// FIXME: don't use cats Ior
trait Differ[T] {
  def diff(inputs: Ior[T, T]): DiffResult

  final def diff(actual: T, expected: T): DiffResult = diff(Ior.Both(actual, expected))
}

object Differ {
  trait ValueDiffer[T] extends Differ[T] {
    override def diff(inputs: Ior[T, T]): DiffResult.ValueResult
  }

  def useEquals[T](implicit en: Encoder[T]): ValueDiffer[T] = {
    new ValueDiffer[T] {
      override def diff(inputs: Ior[T, T]): DiffResult.ValueResult = inputs match {
        case Ior.Both(actual, expected) =>
          DiffResult.ValueResult
            .Both(
              actual = en.apply(actual),
              expected = en.apply(expected),
              isIdentical = actual == expected,
              ignored = false,
            )
        case Ior.Left(actual) =>
          DiffResult.ValueResult.ActualOnly(en.apply(actual), ignored = false)
        case Ior.Right(expected) =>
          DiffResult.ValueResult.ExpectedOnly(en.apply(expected), ignored = false)
      }
    }

  }

  implicit val stringDiff: ValueDiffer[String] = useEquals[String]
  implicit val charDiff: ValueDiffer[Char] = useEquals[Char]
  implicit val booleanDiff: ValueDiffer[Boolean] = useEquals[Boolean]

  implicit def numericDiff[T](implicit numeric: Numeric[T], encoder: Encoder[T]): ValueDiffer[T] = {
    case Ior.Both(actual, expected) => {
      DiffResult.ValueResult.Both(
        encoder.apply(actual),
        encoder.apply(expected),
        isIdentical = numeric.equiv(actual, expected),
        ignored = false,
      )
    }
    case Ior.Left(actual)    => DiffResult.ValueResult.ActualOnly(encoder.apply(actual), ignored = false)
    case Ior.Right(expected) => DiffResult.ValueResult.ExpectedOnly(encoder.apply(expected), ignored = false)
  }

  class RecordDiffer[T](
    // FIXME: nicer type
    fieldDiffers: ListMap[String, (T => Any, Differ[Any])],
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
        DiffResult.RecordResult(diffResults, MatchType.Both, ignored = false)
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
        DiffResult.RecordResult(diffResults, MatchType.ActualOnly, ignored = false)
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
        DiffResult.RecordResult(diffResults, MatchType.ExpectedOnly, ignored = false)
      }
    }
  }
}

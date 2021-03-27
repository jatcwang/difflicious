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
              compareResult = if (actual == expected) CompareResult.Identical else CompareResult.Different,
            )
        case Ior.Left(actual) =>
          DiffResult.ValueResult.ActualOnly(en.apply(actual))
        case Ior.Right(expected) =>
          DiffResult.ValueResult.ExpectedOnly(en.apply(expected))
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
        CompareResult.fromBool(numeric.equiv(actual, expected)),
      )
    }
    case Ior.Left(actual)    => DiffResult.ValueResult.ActualOnly(encoder.apply(actual))
    case Ior.Right(expected) => DiffResult.ValueResult.ExpectedOnly(encoder.apply(expected))
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
        DiffResult.RecordResult(MatchType.Both, diffResults)
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
        DiffResult.RecordResult(MatchType.ActualOnly, diffResults)
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
        DiffResult.RecordResult(MatchType.ExpectedOnly, diffResults)
      }
    }
  }
}

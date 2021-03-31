package difflicious
import io.circe.Encoder
import cats.data.Ior
import difflicious.DiffResult.{MapResult, ListResult}

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

  final class EqualsDiffer[T](isIgnored: Boolean)(implicit encoder: Encoder[T]) extends ValueDiffer[T] {
    override def diff(inputs: Ior[T, T]): DiffResult.ValueResult = inputs match {
      case Ior.Both(actual, expected) =>
        DiffResult.ValueResult
          .Both(
            actual = encoder.apply(actual),
            expected = encoder.apply(expected),
            isOk = isIgnored || actual == expected,
            isIgnored = isIgnored,
          )
      case Ior.Left(actual) =>
        DiffResult.ValueResult.ActualOnly(encoder.apply(actual), isIgnored = isIgnored)
      case Ior.Right(expected) =>
        DiffResult.ValueResult.ExpectedOnly(encoder.apply(expected), isIgnored = isIgnored)
    }

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, EqualsDiffer[T]] = {
      val (step, nextPath) = path.next
      (step, op) match {
        case (Some(_), _)                            => Left(DifferUpdateError.PathTooLong(nextPath))
        case (None, DifferOp.SetIgnored(newIgnored)) => Right(new EqualsDiffer[T](isIgnored = newIgnored))
        case (None, otherOp)                         => Left(DifferUpdateError.InvalidDifferOp(nextPath, otherOp, "EqualsDiffer"))
      }
    }
  }

  def useEquals[T: Encoder]: ValueDiffer[T] = new EqualsDiffer[T](isIgnored = false)

  implicit val stringDiff: ValueDiffer[String] = useEquals[String]
  implicit val charDiff: ValueDiffer[Char] = useEquals[Char]
  implicit val booleanDiff: ValueDiffer[Boolean] = useEquals[Boolean]

  class NumericDiffer[T](isIgnored: Boolean)(implicit numeric: Numeric[T], encoder: Encoder[T]) extends ValueDiffer[T] {
    override def diff(inputs: Ior[T, T]): DiffResult.ValueResult = inputs match {
      case Ior.Both(actual, expected) => {
        DiffResult.ValueResult.Both(
          encoder.apply(actual),
          encoder.apply(expected),
          isOk = isIgnored || numeric.equiv(actual, expected),
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
          .RecordResult(
            diffResults,
            MatchType.Both,
            isIgnored = ignored,
            isOk = ignored || diffResults.values.forall(_.isOk),
          )
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
          .RecordResult(diffResults, MatchType.ActualOnly, isIgnored = ignored, diffResults.values.forall(_.isOk))
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
          .RecordResult(diffResults, MatchType.ExpectedOnly, isIgnored = ignored, diffResults.values.forall(_.isOk))
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
    new MapDiffer(isIgnored = false)

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
      // FIXME: consolidate all 3 cases
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
          isOk = isIgnored || actualOnly.isEmpty && expectedOnly.isEmpty && both.forall(_.value.isOk),
        )
      case Ior.Left(actual) =>
        DiffResult.MapResult(
          entries = actual.map {
            case (k, v) =>
              MapResult.Entry(keyDiffer.diff(Ior.Left(k)), valueDiffer.diff(Ior.Left(v)))
          }.toVector,
          matchType = MatchType.ActualOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case Ior.Right(expected) =>
        DiffResult.MapResult(
          entries = expected.map {
            case (k, v) =>
              MapResult.Entry(keyDiffer.diff(Ior.Right(k)), valueDiffer.diff(Ior.Right(v)))
          }.toVector,
          matchType = MatchType.ActualOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
    }

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, Differ[M[A, B]]] = ???
  }

  implicit def seqDiffer[F[X] <: Seq[X], A: Differ]: SeqDiffer[F, A] =
    new SeqDiffer[F, A](isIgnored = false, matchMethod = MatchMethod.Index)

  // How items are matched with each other when comparing two Seqs
  sealed trait MatchMethod[-A]

  object MatchMethod {
    case object Index extends MatchMethod[Any]
    final case class ByFunc[A, B](func: A => B) extends MatchMethod[A]
  }

  final class SeqDiffer[F[X] <: Seq[X], A](isIgnored: Boolean, matchMethod: MatchMethod[A])(
    implicit itemDiffer: Differ[A],
  ) extends Differ[F[A]] {
    override type R = ListResult

    override def diff(inputs: Ior[F[A], F[A]]): R = inputs match {
      case Ior.Both(actual, expected) => {
        matchMethod match {
          case MatchMethod.Index => {
            val diffResults = actual
              .map(Some(_))
              .zipAll(expected.map(Some(_)), None, None)
              .map {
                case (aOpt, eOpt) =>
                  val ior = Ior.fromOptions(aOpt, eOpt).get // guaranteed one of the Option is Some
                  itemDiffer.diff(ior)
              }
              .toVector

            ListResult(
              items = diffResults,
              matchType = MatchType.Both,
              isIgnored = isIgnored,
              isOk = isIgnored || diffResults.forall(_.isOk),
            )
          }
          case MatchMethod.ByFunc(func) => {
            val matchedIndexes = mutable.BitSet.empty
            val results = mutable.ArrayBuffer.empty[DiffResult]
            val expWithIdx = expected.zipWithIndex
            var overallIsSame = true
            actual.foreach { a =>
              val aMatchVal = func(a)
              val found = expWithIdx.find {
                case (e, idx) =>
                  if (!matchedIndexes.contains(idx) && aMatchVal == func(e)) {
                    val res = itemDiffer.diff(a, e)
                    results += res
                    matchedIndexes += idx
                    overallIsSame &= res.isOk
                    true
                  } else {
                    false
                  }
              }

              // FIXME: perhaps we need to prepend this to the front
              //  of all results for nicer result view?
              if (found.isEmpty) {
                results += itemDiffer.diff(Ior.Left(a))
                overallIsSame = false
              }
            }

            expWithIdx.foreach {
              case (e, idx) =>
                if (!matchedIndexes.contains(idx)) {
                  results += itemDiffer.diff(Ior.Right(e))
                  overallIsSame = false
                }
            }

            ListResult(
              items = results.toVector,
              matchType = MatchType.Both,
              isIgnored = isIgnored,
              isOk = isIgnored || overallIsSame,
            )
          }
        }
      }
      case Ior.Left(actual) =>
        ListResult(
          actual.map { a =>
            itemDiffer.diff(Ior.Left(a))
          }.toVector,
          MatchType.ActualOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case Ior.Right(expected) =>
        ListResult(
          expected.map { a =>
            itemDiffer.diff(Ior.Right(a))
          }.toVector,
          MatchType.ExpectedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
    }

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, Differ[F[A]]] = {
      ???

    }
  }

}

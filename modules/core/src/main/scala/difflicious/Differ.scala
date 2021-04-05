package difflicious
import io.circe.Encoder
import cats.data.Ior
import difflicious.DiffResult.{ListResult, SetResult, MapResult}
import difflicious.DifferOp.MatchBy
import izumi.reflect.Tag

import scala.annotation.nowarn
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
  sealed trait MatchBy[-A] extends DifferOp
  object MatchBy {
    case object Index extends MatchBy[Any]
    final case class ByFunc[A, B](func: A => B, aTag: Tag[A]) extends MatchBy[A]
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

  // FIXME: tuple instances
  class NumericDiffer[T](isIgnored: Boolean)(implicit numeric: Numeric[T], encoder: Encoder[T], tag: Tag[T])
      extends ValueDiffer[T] {
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

  implicit def numericDiff[T](implicit numeric: Numeric[T], encoder: Encoder[T], tag: Tag[T]): ValueDiffer[T] =
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
            case _: DifferOp.MatchBy[_] => Left(DifferUpdateError.InvalidDifferOp(nextPath, op, "record"))
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

    // FIXME:
    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, MapDiffer[A, B, M]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(UpdateStep.DownTypeParam(idx)) =>
          if (idx == 1) { // the value
            valueDiffer.updateWith(nextPath, op).map { newValueDiffer =>
              new MapDiffer[A, B, M](
                isIgnored = isIgnored,
              )(
                keyDiffer,
                newValueDiffer,
              )
            }
          } else
            Left(DifferUpdateError.InvalidTypeParamIndex(path = nextPath, invalidIndex = idx, currentClassName = "Map")) // TODO: more accurate name?
        case Some(_: UpdateStep.DownSubtype | _: UpdateStep.RecordField) =>
          Left(DifferUpdateError.UnexpectedDifferType(nextPath, "Map"))
        case None =>
          op match {
            case DifferOp.SetIgnored(newIsIgnored) =>
              Right(new MapDiffer[A, B, M](isIgnored = newIsIgnored))
            case _: MatchBy[_] =>
              Left(DifferUpdateError.InvalidDifferOp(nextPath, op, "Map"))
          }
      }
    }
  }

  implicit def seqDiffer[F[X] <: Seq[X], A: Differ: Tag]: SeqDiffer[F, A] =
    new SeqDiffer[F, A](isIgnored = false, matchBy = MatchBy.Index)

  final class SeqDiffer[F[X] <: Seq[X], A](isIgnored: Boolean, matchBy: MatchBy[A])(
    implicit itemDiffer: Differ[A],
    tag: Tag[A],
  ) extends Differ[F[A]] {
    override type R = ListResult

    override def diff(inputs: Ior[F[A], F[A]]): R = inputs match {
      case Ior.Both(actual, expected) => {
        matchBy match {
          case MatchBy.Index => {
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
          case MatchBy.ByFunc(func, _) => {
            val (results, allIsOk) = diffMatchByFunc(actual, expected, func, itemDiffer)
            ListResult(
              items = results,
              matchType = MatchType.Both,
              isIgnored = isIgnored,
              isOk = isIgnored || allIsOk,
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

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, SeqDiffer[F, A]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(UpdateStep.DownTypeParam(idx)) =>
          if (idx == 0) {
            itemDiffer.updateWith(nextPath, op).map { newItemDiffer =>
              new SeqDiffer[F, A](
                isIgnored = isIgnored,
                matchBy = matchBy,
              )(
                newItemDiffer,
                tag,
              )
            }
          } else Left(DifferUpdateError.InvalidTypeParamIndex(nextPath, idx, tag.tag.longName))
        case Some(_: UpdateStep.DownSubtype | _: UpdateStep.RecordField) =>
          Left(DifferUpdateError.UnexpectedDifferType(nextPath, s"seq"))
        case None =>
          op match {
            case DifferOp.SetIgnored(newIsIgnored) =>
              Right(new SeqDiffer[F, A](isIgnored = newIsIgnored, matchBy = matchBy))
            case matchBy: DifferOp.MatchBy[_] =>
              matchBy match {
                case MatchBy.Index =>
                  Right(new SeqDiffer[F, A](isIgnored = isIgnored, matchBy = MatchBy.Index))
                case m: MatchBy.ByFunc[_, _] =>
                  if (m.aTag.tag == tag.tag) {
                    Right(new SeqDiffer[F, A](isIgnored = isIgnored, matchBy = m.asInstanceOf[DifferOp.MatchBy[A]]))
                  } else {
                    Left(DifferUpdateError.MatchByTypeMismatch(nextPath, tag.tag, m.aTag.tag))
                  }
              }
          }
      }
    }
  }

  implicit def setDiffer[F[X] <: Set[X], A](implicit itemDiffer: Differ[A], tag: Tag[A]): SetDiffer[F, A] =
    new SetDiffer[F, A](isIgnored = false, itemDiffer, matchFunc = identity)(tag)

  // TODO: maybe find a way for stable ordering (i.e. only order on non-ignored fields)
  final class SetDiffer[F[X] <: Set[X], A](isIgnored: Boolean, itemDiffer: Differ[A], matchFunc: A => Any)(
    implicit tag: Tag[A],
  ) extends Differ[F[A]] {
    override type R = SetResult

    override def diff(inputs: Ior[F[A], F[A]]): R = inputs match {
      case Ior.Left(actual) =>
        SetResult(
          actual.toVector.map { a =>
            itemDiffer.diff(Ior.Left(a))
          },
          MatchType.ActualOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case Ior.Right(expected) =>
        SetResult(
          expected.toVector.map { e =>
            itemDiffer.diff(Ior.Right(e))
          },
          MatchType.ExpectedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case Ior.Both(actual, expected) => {
        val (results, overallIsSame) = diffMatchByFunc(actual.toSeq, expected.toSeq, matchFunc, itemDiffer)
        SetResult(
          results,
          matchType = MatchType.Both,
          isIgnored = isIgnored,
          isOk = isIgnored || overallIsSame,
        )
      }
    }

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, SetDiffer[F, A]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(UpdateStep.DownTypeParam(idx)) =>
          if (idx == 0) {
            itemDiffer.updateWith(nextPath, op).map { updatedItemDiffer =>
              new SetDiffer[F, A](isIgnored = isIgnored, updatedItemDiffer, matchFunc)
            }
          } else Left(DifferUpdateError.InvalidTypeParamIndex(nextPath, idx, "Set"))
        case Some(_: UpdateStep.RecordField | _: UpdateStep.DownSubtype) =>
          Left(DifferUpdateError.UnexpectedDifferType(nextPath, "Set"))
        case None =>
          op match {
            case DifferOp.SetIgnored(newIsIgnored) =>
              Right(new SetDiffer[F, A](isIgnored = newIsIgnored, itemDiffer, matchFunc))
            case m: MatchBy[_] =>
              m match {
                case MatchBy.Index => Left(DifferUpdateError.InvalidDifferOp(nextPath, m, "Set"))
                case m: MatchBy.ByFunc[_, _] =>
                  if (m.aTag.tag == tag.tag) {
                    Right(
                      new SetDiffer[F, A](
                        isIgnored = isIgnored,
                        itemDiffer = itemDiffer,
                        matchFunc = m.func.asInstanceOf[A => Any],
                      ),
                    )
                  } else {
                    Left(DifferUpdateError.MatchByTypeMismatch(nextPath, tag.tag, m.aTag.tag))
                  }
              }
          }

      }
    }

    @nowarn("msg=.*deprecated.*")
    def matchBy[B](func: A => B): SetDiffer[F, A] = {
      updateWith(UpdatePath.current, MatchBy.ByFunc(func, tag)).right.get
    }
  }

  // Given two lists of item, find "matching" items using te provided function
  // (where "matching" means ==). For example we might want to items by
  // person name.
  private def diffMatchByFunc[A](
    actual: Seq[A],
    expected: Seq[A],
    func: A => Any,
    itemDiffer: Differ[A],
  ): (Vector[DiffResult], Boolean) = {
    val matchedIndexes = mutable.BitSet.empty
    val results = mutable.ArrayBuffer.empty[DiffResult]
    val expWithIdx = expected.zipWithIndex
    var allIsOk = true
    actual.foreach { a =>
      val aMatchVal = func(a)
      val found = expWithIdx.find {
        case (e, idx) =>
          if (!matchedIndexes.contains(idx) && aMatchVal == func(e)) {
            val res = itemDiffer.diff(a, e)
            results += res
            matchedIndexes += idx
            allIsOk &= res.isOk
            true
          } else {
            false
          }
      }

      // FIXME: perhaps we need to prepend this to the front
      //  of all results for nicer result view?
      if (found.isEmpty) {
        results += itemDiffer.diff(Ior.Left(a))
        allIsOk = false
      }
    }

    expWithIdx.foreach {
      case (e, idx) =>
        if (!matchedIndexes.contains(idx)) {
          results += itemDiffer.diff(Ior.Right(e))
          allIsOk = false
        }
    }

    (results.toVector, allIsOk)
  }

}

package difflicious
import cats.data.Ior
import difflicious.DiffResult.{ListResult, SetResult, ValueResult, MapResult}
import difflicious.DifferOp.MatchBy
import difflicious.differ.NumericDiffer
import difflicious.internal.EitherGetSyntax._
import difflicious.utils.{TypeName, AsMap, AsSeq, AsSet}
import izumi.reflect.macrortti.LTag

import scala.collection.mutable

// FIXME: anyval?
// FIXME: don't use cats Ior
trait Differ[T] {
  type R <: DiffResult

  def diff(inputs: Ior[T, T]): R

  final def diff(obtained: T, expected: T): R = diff(Ior.Both(obtained, expected))

  /**
    * Create an new Differ instance where the given path will produce an ignored DiffResult
    */
  def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, Differ[T]]
}

sealed trait DifferOp

object DifferOp {
  val ignore: SetIgnored = SetIgnored(true)
  val unignored: SetIgnored = SetIgnored(false)

  final case class SetIgnored(isIgnored: Boolean) extends DifferOp
  sealed trait MatchBy[-A] extends DifferOp
  object MatchBy {
    case object Index extends MatchBy[Any]
    final case class ByFunc[A, B] private[difflicious] (func: A => B, aTag: LTag[A]) extends MatchBy[A]

    def func[A, B](func: A => B)(implicit aTag: LTag[A]): ByFunc[A, B] = ByFunc(func, aTag)
  }

}

object Differ extends DifferTupleInstances with DifferGen {

  def apply[A](implicit differ: Differ[A]): Differ[A] = differ

  // FIXME: better string diff (edit distance and a description of how to get there? this can help especially in cases like extra space or special char)

  // FIXME: need tag
  trait ValueDiffer[T] extends Differ[T] {
    final override type R = DiffResult.ValueResult

    override def diff(inputs: Ior[T, T]): R
  }

  final class EqualsDiffer[T](isIgnored: Boolean, valueToString: T => String) extends ValueDiffer[T] {
    override def diff(inputs: Ior[T, T]): DiffResult.ValueResult = inputs match {
      case Ior.Both(obtained, expected) =>
        DiffResult.ValueResult
          .Both(
            obtained = valueToString(obtained),
            expected = valueToString(expected),
            isSame = obtained == expected,
            isIgnored = isIgnored,
          )
      case Ior.Left(obtained) =>
        DiffResult.ValueResult.ObtainedOnly(valueToString(obtained), isIgnored = isIgnored)
      case Ior.Right(expected) =>
        DiffResult.ValueResult.ExpectedOnly(valueToString(expected), isIgnored = isIgnored)
    }

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, EqualsDiffer[T]] = {
      val (step, nextPath) = path.next
      (step, op) match {
        case (Some(_), _) => Left(DifferUpdateError.PathTooLong(nextPath))
        case (None, DifferOp.SetIgnored(newIgnored)) =>
          Right(new EqualsDiffer[T](isIgnored = newIgnored, valueToString = valueToString))
        case (None, otherOp) => Left(DifferUpdateError.InvalidDifferOp(nextPath, otherOp, "EqualsDiffer"))
      }
    }
  }

  def useEquals[T](valueToString: T => String): EqualsDiffer[T] =
    new EqualsDiffer[T](isIgnored = false, valueToString = valueToString)

  // FIXME: better reporting for string error
  implicit val stringDiff: ValueDiffer[String] = useEquals[String](str => s""""$str"""")
  implicit val charDiff: ValueDiffer[Char] = useEquals[Char](c => s"'$c'")
  implicit val booleanDiff: ValueDiffer[Boolean] = useEquals[Boolean](_.toString)

  // FIXME: java bigint and decimal
  implicit val intDiff: NumericDiffer[Int] = NumericDiffer.make[Int]
  implicit val doubleDiff: NumericDiffer[Double] = NumericDiffer.make[Double]
  implicit val shortDiff: NumericDiffer[Short] = NumericDiffer.make[Short]
  implicit val byteDiff: NumericDiffer[Byte] = NumericDiffer.make[Byte]
  implicit val longDiff: NumericDiffer[Long] = NumericDiffer.make[Long]
  implicit val bigDecimalDiff: NumericDiffer[BigDecimal] = NumericDiffer.make[BigDecimal]
  implicit val bigIntDiff: NumericDiffer[BigInt] = NumericDiffer.make[BigInt]

  // FIXME: tuple instances
  implicit def mapDiffer[M[_, _], A, B](
    implicit keyDiffer: ValueDiffer[A],
    valueDiffer: Differ[B],
    tag: LTag[M[A, B]],
    asMap: AsMap[M],
  ): MapDiffer[M, A, B] = {
    val typeName: TypeName = TypeName.fromLightTypeTag(tag.tag)
    new MapDiffer(
      isIgnored = false,
      keyDiffer = keyDiffer,
      valueDiffer = valueDiffer,
      typeName = typeName,
      asMap = asMap,
    )
  }

  // FIXME: probably want some sort of ordering to maintain consistent order
  class MapDiffer[M[_, _], A, B](
    isIgnored: Boolean,
    keyDiffer: ValueDiffer[A],
    valueDiffer: Differ[B],
    typeName: TypeName,
    asMap: AsMap[M],
  ) extends Differ[M[A, B]] {
    override type R = MapResult

    override def diff(inputs: Ior[M[A, B], M[A, B]]): R = inputs.bimap(asMap.asMap, asMap.asMap) match {
      // FIXME: consolidate all 3 cases
      case Ior.Both(obtained, expected) =>
        val obtainedOnly = mutable.ArrayBuffer.empty[MapResult.Entry]
        val both = mutable.ArrayBuffer.empty[MapResult.Entry]
        val expectedOnly = mutable.ArrayBuffer.empty[MapResult.Entry]
        obtained.foreach {
          case (k, actualV) =>
            expected.get(k) match {
              case Some(expectedV) =>
                both += MapResult.Entry(
                  mapKeyToString(k, keyDiffer),
                  valueDiffer.diff(actualV, expectedV),
                )
              case None =>
                obtainedOnly += MapResult.Entry(
                  mapKeyToString(k, keyDiffer),
                  valueDiffer.diff(Ior.Left(actualV)),
                )
            }
        }
        expected.foreach {
          case (k, expectedV) =>
            if (obtained.contains(k)) {
              // Do nothing, already compared when iterating through obtained
            } else {
              expectedOnly += MapResult.Entry(
                mapKeyToString(k, keyDiffer),
                valueDiffer.diff(Ior.Right(expectedV)),
              )
            }
        }
        (obtainedOnly ++ both ++ expectedOnly).toVector
        MapResult(
          typeName = typeName,
          (obtainedOnly ++ both ++ expectedOnly).toVector,
          MatchType.Both,
          isIgnored = isIgnored,
          isOk = isIgnored || obtainedOnly.isEmpty && expectedOnly.isEmpty && both.forall(_.value.isOk),
        )
      case Ior.Left(obtained) =>
        DiffResult.MapResult(
          typeName = typeName,
          entries = obtained.map {
            case (k, v) =>
              MapResult.Entry(mapKeyToString(k, keyDiffer), valueDiffer.diff(Ior.Left(v)))
          }.toVector,
          matchType = MatchType.ObtainedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case Ior.Right(expected) =>
        DiffResult.MapResult(
          typeName = typeName,
          entries = expected.map {
            case (k, v) =>
              MapResult.Entry(mapKeyToString(k, keyDiffer), valueDiffer.diff(Ior.Right(v)))
          }.toVector,
          matchType = MatchType.ExpectedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
    }

    // FIXME:
    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, MapDiffer[M, A, B]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(fieldName) =>
          if (fieldName == "each") {
            valueDiffer.updateWith(nextPath, op).map { newValueDiffer =>
              new MapDiffer[M, A, B](
                isIgnored = isIgnored,
                keyDiffer = keyDiffer,
                valueDiffer = newValueDiffer,
                typeName = typeName,
                asMap = asMap,
              )
            }
          } else
            Left(DifferUpdateError.NonExistentField(path = nextPath, fieldName))
        case None =>
          op match {
            case DifferOp.SetIgnored(newIsIgnored) =>
              Right(
                new MapDiffer[M, A, B](
                  isIgnored = newIsIgnored,
                  keyDiffer = keyDiffer,
                  valueDiffer = valueDiffer,
                  typeName = typeName,
                  asMap = asMap,
                ),
              )
            case _: MatchBy[_] =>
              Left(DifferUpdateError.InvalidDifferOp(nextPath, op, "MapDiffer"))
          }
      }
    }
  }

  implicit def seqDiffer[F[_], A](
    implicit itemDiffer: Differ[A],
    fullTag: LTag[F[A]],
    itemTag: LTag[A],
    asSeq: AsSeq[F],
  ): SeqDiffer[F, A] = {
    val typeName = TypeName.fromLightTypeTag(fullTag.tag)
    SeqDiffer.create(
      itemDiffer = itemDiffer,
      typeName = typeName,
      itemTag = itemTag,
      asSeq = asSeq,
    )
  }

  final class SeqDiffer[F[_], A](
    isIgnored: Boolean,
    matchBy: MatchBy[A],
    itemDiffer: Differ[A],
    typeName: TypeName,
    itemTag: LTag[A],
    asSeq: AsSeq[F],
  ) extends Differ[F[A]] {
    override type R = ListResult

    override def diff(inputs: Ior[F[A], F[A]]): R = inputs.bimap(asSeq.asSeq, asSeq.asSeq) match {
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
              typeName = typeName,
              items = diffResults,
              matchType = MatchType.Both,
              isIgnored = isIgnored,
              isOk = isIgnored || diffResults.forall(_.isOk),
            )
          }
          case MatchBy.ByFunc(func, _) => {
            val (results, allIsOk) = diffMatchByFunc(actual, expected, func, itemDiffer)
            ListResult(
              typeName = typeName,
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
          typeName = typeName,
          items = actual.map { a =>
            itemDiffer.diff(Ior.Left(a))
          }.toVector,
          matchType = MatchType.ObtainedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case Ior.Right(expected) =>
        ListResult(
          typeName = typeName,
          items = expected.map { a =>
            itemDiffer.diff(Ior.Right(a))
          }.toVector,
          matchType = MatchType.ExpectedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
    }

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, SeqDiffer[F, A]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(fieldName) =>
          if (fieldName == "each") {
            itemDiffer.updateWith(nextPath, op).map { newItemDiffer =>
              new SeqDiffer[F, A](
                isIgnored = isIgnored,
                matchBy = matchBy,
                itemDiffer = newItemDiffer,
                typeName = typeName,
                itemTag = itemTag,
                asSeq = asSeq,
              )
            }
          } else Left(DifferUpdateError.NonExistentField(nextPath, fieldName))
        case None =>
          op match {
            case DifferOp.SetIgnored(newIsIgnored) =>
              Right(
                new SeqDiffer[F, A](
                  isIgnored = newIsIgnored,
                  matchBy = matchBy,
                  itemDiffer = itemDiffer,
                  typeName = typeName,
                  itemTag = itemTag,
                  asSeq = asSeq,
                ),
              )
            case matchBy: DifferOp.MatchBy[_] =>
              matchBy match {
                case MatchBy.Index =>
                  Right(
                    new SeqDiffer[F, A](
                      isIgnored = isIgnored,
                      matchBy = MatchBy.Index,
                      itemDiffer = itemDiffer,
                      typeName = typeName,
                      itemTag = itemTag,
                      asSeq = asSeq,
                    ),
                  )
                case m: MatchBy.ByFunc[_, _] =>
                  if (m.aTag.tag == itemTag.tag) {
                    Right(
                      new SeqDiffer[F, A](
                        isIgnored = isIgnored,
                        matchBy = m.asInstanceOf[DifferOp.MatchBy[A]],
                        itemDiffer = itemDiffer,
                        typeName = typeName,
                        itemTag = itemTag,
                        asSeq = asSeq,
                      ),
                    )
                  } else {
                    Left(
                      DifferUpdateError
                        .MatchByTypeMismatch(nextPath, obtainedTag = m.aTag.tag, expectedTag = itemTag.tag),
                    )
                  }
              }
          }
      }
    }

    def matchBy[B](func: A => B): SeqDiffer[F, A] = {
      // Should always succeed, because method signature guarantees func takes an A
      updateWith(UpdatePath.current, MatchBy.ByFunc(func, itemTag)).unsafeGet
    }

    def matchByIndex: SeqDiffer[F, A] = {
      // Should always succeed, because method signature guarantees func takes an A
      updateWith(UpdatePath.current, MatchBy.Index).unsafeGet
    }
  }

  object SeqDiffer {
    def create[F[_], A](
      itemDiffer: Differ[A],
      typeName: TypeName,
      itemTag: LTag[A],
      asSeq: AsSeq[F],
    ): SeqDiffer[F, A] = new SeqDiffer[F, A](
      isIgnored = false,
      matchBy = MatchBy.Index,
      itemDiffer = itemDiffer,
      typeName = typeName,
      itemTag = itemTag,
      asSeq = asSeq,
    )
  }

  implicit def setDiffer[F[_], A](
    implicit itemDiffer: Differ[A],
    fullTag: LTag[F[A]],
    itemTag: LTag[A],
    asSet: AsSet[F],
  ): SetDiffer[F, A] = {
    val typeName = TypeName.fromLightTypeTag(fullTag.tag)
    SetDiffer.create[F, A](
      itemDiffer = itemDiffer,
      typeName = typeName,
      itemTag = itemTag,
      asSet = asSet,
    )
  }

  object SetDiffer {
    def create[F[_], A](
      itemDiffer: Differ[A],
      typeName: TypeName,
      itemTag: LTag[A],
      asSet: AsSet[F],
    ): SetDiffer[F, A] = new SetDiffer[F, A](
      isIgnored = false,
      itemDiffer,
      matchFunc = identity,
      typeName = typeName,
      itemTag = itemTag,
      asSet = asSet,
    )
  }

  // TODO: maybe find a way for stable ordering (i.e. only order on non-ignored fields)
  final class SetDiffer[F[_], A](
    isIgnored: Boolean,
    itemDiffer: Differ[A],
    matchFunc: A => Any,
    typeName: TypeName,
    itemTag: LTag[A],
    asSet: AsSet[F],
  ) extends Differ[F[A]] {
    override type R = SetResult

    override def diff(inputs: Ior[F[A], F[A]]): R = inputs.bimap(asSet.asSet, asSet.asSet) match {
      case Ior.Left(actual) =>
        SetResult(
          typeName = typeName,
          actual.toVector.map { a =>
            itemDiffer.diff(Ior.Left(a))
          },
          MatchType.ObtainedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case Ior.Right(expected) =>
        SetResult(
          typeName = typeName,
          items = expected.toVector.map { e =>
            itemDiffer.diff(Ior.Right(e))
          },
          matchType = MatchType.ExpectedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case Ior.Both(obtained, expected) => {
        val (results, overallIsSame) = diffMatchByFunc(obtained.toSeq, expected.toSeq, matchFunc, itemDiffer)
        SetResult(
          typeName = typeName,
          items = results,
          matchType = MatchType.Both,
          isIgnored = isIgnored,
          isOk = isIgnored || overallIsSame,
        )
      }
    }

    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, SetDiffer[F, A]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(fieldName) =>
          if (fieldName == "each") {
            itemDiffer.updateWith(nextPath, op).map { updatedItemDiffer =>
              new SetDiffer[F, A](
                isIgnored = isIgnored,
                itemDiffer = updatedItemDiffer,
                matchFunc = matchFunc,
                typeName = typeName,
                itemTag = itemTag,
                asSet = asSet,
              )
            }
          } else Left(DifferUpdateError.NonExistentField(nextPath, fieldName))
        case None =>
          op match {
            case DifferOp.SetIgnored(newIsIgnored) =>
              Right(
                new SetDiffer[F, A](
                  isIgnored = newIsIgnored,
                  itemDiffer = itemDiffer,
                  matchFunc = matchFunc,
                  typeName = typeName,
                  itemTag = itemTag,
                  asSet = asSet,
                ),
              )
            case m: MatchBy[_] =>
              m match {
                case MatchBy.Index => Left(DifferUpdateError.InvalidDifferOp(nextPath, m, "Set"))
                case m: MatchBy.ByFunc[_, _] =>
                  if (m.aTag.tag == itemTag.tag) {
                    Right(
                      new SetDiffer[F, A](
                        isIgnored = isIgnored,
                        itemDiffer = itemDiffer,
                        matchFunc = m.func.asInstanceOf[A => Any],
                        typeName = typeName,
                        itemTag = itemTag,
                        asSet = asSet,
                      ),
                    )
                  } else {
                    Left(DifferUpdateError.MatchByTypeMismatch(nextPath, m.aTag.tag, itemTag.tag))
                  }
              }
          }

      }
    }

    def matchBy[B](func: A => B): SetDiffer[F, A] = {
      // Should always succeed, because method signature guarantees func takes an A
      updateWith(UpdatePath.current, MatchBy.ByFunc(func, itemTag)).unsafeGet
    }
  }

  // Given two lists of item, find "matching" items using te provided function
  // (where "matching" means ==). For example we might want to items by
  // person name.
  private def diffMatchByFunc[A](
    obtained: Seq[A],
    expected: Seq[A],
    func: A => Any,
    itemDiffer: Differ[A],
  ): (Vector[DiffResult], Boolean) = {
    val matchedIndexes = mutable.BitSet.empty
    val results = mutable.ArrayBuffer.empty[DiffResult]
    val expWithIdx = expected.zipWithIndex
    var allIsOk = true
    obtained.foreach { a =>
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

  private def mapKeyToString[T](k: T, keyDiffer: ValueDiffer[T]): String = {
    keyDiffer.diff(Ior.Left(k)) match {
      case r: ValueResult.ObtainedOnly => r.obtained
      // $COVERAGE-OFF$
      case r: ValueResult.Both         => r.obtained
      case r: ValueResult.ExpectedOnly => r.expected
      // $COVERAGE-ON$
    }
  }

}

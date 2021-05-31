package difflicious
import difflicious.DiffResult.{ListResult, SetResult, ValueResult, MapResult}
import difflicious.ConfigureOp.PairBy
import difflicious.differ.NumericDiffer
import difflicious.internal.ConfigureImpl
import difflicious.internal.EitherGetSyntax._
import difflicious.utils.{TypeName, AsMap, AsSeq, AsSet}
import izumi.reflect.macrortti.LTag

import scala.collection.mutable

trait Differ[T] extends ConfigureImpl[T] {
  type R <: DiffResult

  def diff(inputs: DiffInput[T]): R

  final def diff(obtained: T, expected: T): R = diff(DiffInput.Both(obtained, expected))

  /**
    * Attempt to change the configuration of this Differ.
    * If successful, a new differ with the updated configuration will be returned.
    *
    * The configuration change can fail due to
    * - bad "path" that does not match the internal structure of the Differ
    * - The path resolved correctly, but the configuration update operation cannot be applied for that part of the Differ
    *   (e.g. wrong type or wrong operation)
    *
    * @param path The path to traverse to the sub-Differ
    * @param operation The configuration change operation you want to perform on the target sub-Differ
    */
  def configureRaw(path: ConfigurePath, operation: ConfigureOp): Either[ConfigureError, Differ[T]]
}

/**
  * The configuration change operation we want to perform on a differ.
  * For example we might want to:
  *
  * - Mark the current differ as ignored so its comparison never fails
  * - Change a SeqDiffer to pair by a field instead of index
  */
sealed trait ConfigureOp

object ConfigureOp {
  val ignore: SetIgnored = SetIgnored(true)
  val unignored: SetIgnored = SetIgnored(false)

  final case class SetIgnored(isIgnored: Boolean) extends ConfigureOp
  sealed trait PairBy[-A] extends ConfigureOp
  object PairBy {
    case object Index extends PairBy[Any]
    final case class ByFunc[A, B] private[difflicious] (func: A => B, aTag: LTag[A]) extends PairBy[A]

    def func[A, B](func: A => B)(implicit aTag: LTag[A]): ByFunc[A, B] = ByFunc(func, aTag)
  }

}

object Differ extends DifferTupleInstances with DifferGen {

  def apply[A](implicit differ: Differ[A]): Differ[A] = differ

  // FIXME: better string diff (edit distance and a description of how to get there? this can help especially in cases like extra space or special char)

  // FIXME: need tag
  trait ValueDiffer[T] extends Differ[T] {
    final override type R = DiffResult.ValueResult

    override def diff(inputs: DiffInput[T]): R
  }

  final class EqualsDiffer[T](isIgnored: Boolean, valueToString: T => String) extends ValueDiffer[T] {
    override def diff(inputs: DiffInput[T]): DiffResult.ValueResult = inputs match {
      case DiffInput.Both(obtained, expected) =>
        DiffResult.ValueResult
          .Both(
            obtained = valueToString(obtained),
            expected = valueToString(expected),
            isSame = obtained == expected,
            isIgnored = isIgnored,
          )
      case DiffInput.ObtainedOnly(obtained) =>
        DiffResult.ValueResult.ObtainedOnly(valueToString(obtained), isIgnored = isIgnored)
      case DiffInput.ExpectedOnly(expected) =>
        DiffResult.ValueResult.ExpectedOnly(valueToString(expected), isIgnored = isIgnored)
    }

    override def configureRaw(path: ConfigurePath, op: ConfigureOp): Either[ConfigureError, EqualsDiffer[T]] = {
      val (step, nextPath) = path.next
      (step, op) match {
        case (Some(_), _) => Left(ConfigureError.PathTooLong(nextPath))
        case (None, ConfigureOp.SetIgnored(newIgnored)) =>
          Right(new EqualsDiffer[T](isIgnored = newIgnored, valueToString = valueToString))
        case (None, otherOp) => Left(ConfigureError.InvalidDifferOp(nextPath, otherOp, "EqualsDiffer"))
      }
    }
  }

  def useEquals[T](valueToString: T => String): EqualsDiffer[T] =
    new EqualsDiffer[T](isIgnored = false, valueToString = valueToString)

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

    override def diff(inputs: DiffInput[M[A, B]]): R = inputs.map(asMap.asMap) match {
      // FIXME: consolidate all 3 cases
      case DiffInput.Both(obtained, expected) =>
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
                  valueDiffer.diff(DiffInput.ObtainedOnly(actualV)),
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
                valueDiffer.diff(DiffInput.ExpectedOnly(expectedV)),
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
      case DiffInput.ObtainedOnly(obtained) =>
        DiffResult.MapResult(
          typeName = typeName,
          entries = obtained.map {
            case (k, v) =>
              MapResult.Entry(mapKeyToString(k, keyDiffer), valueDiffer.diff(DiffInput.ObtainedOnly(v)))
          }.toVector,
          matchType = MatchType.ObtainedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case DiffInput.ExpectedOnly(expected) =>
        DiffResult.MapResult(
          typeName = typeName,
          entries = expected.map {
            case (k, v) =>
              MapResult.Entry(mapKeyToString(k, keyDiffer), valueDiffer.diff(DiffInput.ExpectedOnly(v)))
          }.toVector,
          matchType = MatchType.ExpectedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
    }

    override def configureRaw(path: ConfigurePath, op: ConfigureOp): Either[ConfigureError, MapDiffer[M, A, B]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(fieldName) =>
          if (fieldName == "each") {
            valueDiffer.configureRaw(nextPath, op).map { newValueDiffer =>
              new MapDiffer[M, A, B](
                isIgnored = isIgnored,
                keyDiffer = keyDiffer,
                valueDiffer = newValueDiffer,
                typeName = typeName,
                asMap = asMap,
              )
            }
          } else
            Left(ConfigureError.NonExistentField(path = nextPath, fieldName))
        case None =>
          op match {
            case ConfigureOp.SetIgnored(newIsIgnored) =>
              Right(
                new MapDiffer[M, A, B](
                  isIgnored = newIsIgnored,
                  keyDiffer = keyDiffer,
                  valueDiffer = valueDiffer,
                  typeName = typeName,
                  asMap = asMap,
                ),
              )
            case _: PairBy[_] =>
              Left(ConfigureError.InvalidDifferOp(nextPath, op, "MapDiffer"))
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
    pairBy: PairBy[A],
    itemDiffer: Differ[A],
    typeName: TypeName,
    itemTag: LTag[A],
    asSeq: AsSeq[F],
  ) extends Differ[F[A]] {
    override type R = ListResult

    override def diff(inputs: DiffInput[F[A]]): R = inputs.map(asSeq.asSeq) match {
      case DiffInput.Both(actual, expected) => {
        pairBy match {
          case PairBy.Index => {
            val diffResults = actual
              .map(Some(_))
              .zipAll(expected.map(Some(_)), None, None)
              .map {
                case (Some(ob), Some(exp)) => itemDiffer.diff(DiffInput.Both(ob, exp))
                case (Some(ob), None)      => itemDiffer.diff(DiffInput.ObtainedOnly(ob))
                case (None, Some(exp))     => itemDiffer.diff(DiffInput.ExpectedOnly(exp))
                case (None, None) =>
                  throw new RuntimeException(
                    "Unexpected: Both obtained and expected side is None in SeqDiffer. " +
                      "This is most likely a diffilicious bug",
                  )
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
          case PairBy.ByFunc(func, _) => {
            val (results, allIsOk) = diffPairByFunc(actual, expected, func, itemDiffer)
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
      case DiffInput.ObtainedOnly(actual) =>
        ListResult(
          typeName = typeName,
          items = actual.map { a =>
            itemDiffer.diff(DiffInput.ObtainedOnly(a))
          }.toVector,
          matchType = MatchType.ObtainedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case DiffInput.ExpectedOnly(expected) =>
        ListResult(
          typeName = typeName,
          items = expected.map { a =>
            itemDiffer.diff(DiffInput.ExpectedOnly(a))
          }.toVector,
          matchType = MatchType.ExpectedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
    }

    override def configureRaw(path: ConfigurePath, op: ConfigureOp): Either[ConfigureError, SeqDiffer[F, A]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(fieldName) =>
          if (fieldName == "each") {
            itemDiffer.configureRaw(nextPath, op).map { newItemDiffer =>
              new SeqDiffer[F, A](
                isIgnored = isIgnored,
                pairBy = pairBy,
                itemDiffer = newItemDiffer,
                typeName = typeName,
                itemTag = itemTag,
                asSeq = asSeq,
              )
            }
          } else Left(ConfigureError.NonExistentField(nextPath, fieldName))
        case None =>
          op match {
            case ConfigureOp.SetIgnored(newIsIgnored) =>
              Right(
                new SeqDiffer[F, A](
                  isIgnored = newIsIgnored,
                  pairBy = pairBy,
                  itemDiffer = itemDiffer,
                  typeName = typeName,
                  itemTag = itemTag,
                  asSeq = asSeq,
                ),
              )
            case matchBy: ConfigureOp.PairBy[_] =>
              matchBy match {
                case PairBy.Index =>
                  Right(
                    new SeqDiffer[F, A](
                      isIgnored = isIgnored,
                      pairBy = PairBy.Index,
                      itemDiffer = itemDiffer,
                      typeName = typeName,
                      itemTag = itemTag,
                      asSeq = asSeq,
                    ),
                  )
                case m: PairBy.ByFunc[_, _] =>
                  if (m.aTag.tag == itemTag.tag) {
                    Right(
                      new SeqDiffer[F, A](
                        isIgnored = isIgnored,
                        pairBy = m.asInstanceOf[ConfigureOp.PairBy[A]],
                        itemDiffer = itemDiffer,
                        typeName = typeName,
                        itemTag = itemTag,
                        asSeq = asSeq,
                      ),
                    )
                  } else {
                    Left(
                      ConfigureError
                        .PairByTypeMismatch(nextPath, obtainedTag = m.aTag.tag, expectedTag = itemTag.tag),
                    )
                  }
              }
          }
      }
    }

    // FIXME: Move this to be possible on any Differ[F[A]] where F is pairable
    def pairBy[B](func: A => B): SeqDiffer[F, A] = {
      // Should always succeed, because method signature guarantees func takes an A
      configureRaw(ConfigurePath.current, PairBy.ByFunc(func, itemTag)).unsafeGet
    }

    def pairByIndex: SeqDiffer[F, A] = {
      // Should always succeed, because method signature guarantees func takes an A
      configureRaw(ConfigurePath.current, PairBy.Index).unsafeGet
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
      pairBy = PairBy.Index,
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

    override def diff(inputs: DiffInput[F[A]]): R = inputs.map(asSet.asSet) match {
      case DiffInput.ObtainedOnly(actual) =>
        SetResult(
          typeName = typeName,
          actual.toVector.map { a =>
            itemDiffer.diff(DiffInput.ObtainedOnly(a))
          },
          MatchType.ObtainedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case DiffInput.ExpectedOnly(expected) =>
        SetResult(
          typeName = typeName,
          items = expected.toVector.map { e =>
            itemDiffer.diff(DiffInput.ExpectedOnly(e))
          },
          matchType = MatchType.ExpectedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
      case DiffInput.Both(obtained, expected) => {
        val (results, overallIsSame) = diffPairByFunc(obtained.toSeq, expected.toSeq, matchFunc, itemDiffer)
        SetResult(
          typeName = typeName,
          items = results,
          matchType = MatchType.Both,
          isIgnored = isIgnored,
          isOk = isIgnored || overallIsSame,
        )
      }
    }

    override def configureRaw(path: ConfigurePath, op: ConfigureOp): Either[ConfigureError, SetDiffer[F, A]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(fieldName) =>
          if (fieldName == "each") {
            itemDiffer.configureRaw(nextPath, op).map { updatedItemDiffer =>
              new SetDiffer[F, A](
                isIgnored = isIgnored,
                itemDiffer = updatedItemDiffer,
                matchFunc = matchFunc,
                typeName = typeName,
                itemTag = itemTag,
                asSet = asSet,
              )
            }
          } else Left(ConfigureError.NonExistentField(nextPath, fieldName))
        case None =>
          op match {
            case ConfigureOp.SetIgnored(newIsIgnored) =>
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
            case m: PairBy[_] =>
              m match {
                case PairBy.Index => Left(ConfigureError.InvalidDifferOp(nextPath, m, "Set"))
                case m: PairBy.ByFunc[_, _] =>
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
                    Left(ConfigureError.PairByTypeMismatch(nextPath, m.aTag.tag, itemTag.tag))
                  }
              }
          }

      }
    }

    def pairBy[B](func: A => B): SetDiffer[F, A] = {
      // Should always succeed, because method signature guarantees func takes an A
      configureRaw(ConfigurePath.current, PairBy.ByFunc(func, itemTag)).unsafeGet
    }
  }

  // Given two lists of item, find "matching" items using te provided function
  // (where "matching" means ==). For example we might want to items by
  // person name.
  private def diffPairByFunc[A](
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
        results += itemDiffer.diff(DiffInput.ObtainedOnly(a))
        allIsOk = false
      }
    }

    expWithIdx.foreach {
      case (e, idx) =>
        if (!matchedIndexes.contains(idx)) {
          results += itemDiffer.diff(DiffInput.ExpectedOnly(e))
          allIsOk = false
        }
    }

    (results.toVector, allIsOk)
  }

  private def mapKeyToString[T](k: T, keyDiffer: ValueDiffer[T]): String = {
    keyDiffer.diff(DiffInput.ObtainedOnly(k)) match {
      case r: ValueResult.ObtainedOnly => r.obtained
      // $COVERAGE-OFF$
      case r: ValueResult.Both         => r.obtained
      case r: ValueResult.ExpectedOnly => r.expected
      // $COVERAGE-ON$
    }
  }

}

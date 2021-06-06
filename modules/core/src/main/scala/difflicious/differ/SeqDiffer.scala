package difflicious.differ

import difflicious.DiffResult.ListResult
import difflicious.utils.{TypeName, SeqLike}
import difflicious.ConfigureOp.PairBy
import izumi.reflect.macrortti.LTag
import difflicious.{PairType, Differ, DiffResult, ConfigureOp, ConfigureError, ConfigurePath, DiffInput}
import SeqDiffer.diffPairByFunc

import scala.collection.mutable

final class SeqDiffer[F[_], A](
  isIgnored: Boolean,
  pairBy: PairBy[A],
  itemDiffer: Differ[A],
  typeName: TypeName,
  override protected val tag: LTag[F[A]],
  itemTag: LTag[A],
  asSeq: SeqLike[F],
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
              case (None, None)          =>
                // $COVERAGE-OFF$
                throw new RuntimeException(
                  "Unexpected: Both obtained and expected side is None in SeqDiffer. " +
                    "This shouldn't happen and is most likely a difflicious bug",
                )
              // $COVERAGE-ON$
            }
            .toVector

          ListResult(
            typeName = typeName,
            items = diffResults,
            pairType = PairType.Both,
            isIgnored = isIgnored,
            isOk = isIgnored || diffResults.forall(_.isOk),
          )
        }
        case PairBy.ByFunc(func, _) => {
          val (results, allIsOk) = diffPairByFunc(actual, expected, func, itemDiffer)
          ListResult(
            typeName = typeName,
            items = results,
            pairType = PairType.Both,
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
        pairType = PairType.ObtainedOnly,
        isIgnored = isIgnored,
        isOk = isIgnored,
      )
    case DiffInput.ExpectedOnly(expected) =>
      ListResult(
        typeName = typeName,
        items = expected.map { a =>
          itemDiffer.diff(DiffInput.ExpectedOnly(a))
        }.toVector,
        pairType = PairType.ExpectedOnly,
        isIgnored = isIgnored,
        isOk = isIgnored,
      )
  }

  override def configureIgnored(newIgnored: Boolean): Differ[F[A]] =
    new SeqDiffer[F, A](
      isIgnored = newIgnored,
      pairBy = pairBy,
      itemDiffer = itemDiffer,
      typeName = typeName,
      tag = tag,
      itemTag = itemTag,
      asSeq = asSeq,
    )

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[F[A]]] =
    if (step == "each") {
      itemDiffer.configureRaw(nextPath, op).map { newItemDiffer =>
        new SeqDiffer[F, A](
          isIgnored = isIgnored,
          pairBy = pairBy,
          itemDiffer = newItemDiffer,
          typeName = typeName,
          tag = tag,
          itemTag = itemTag,
          asSeq = asSeq,
        )
      }
    } else Left(ConfigureError.NonExistentField(nextPath, "SeqDiffer"))

  override def configurePairBy(path: ConfigurePath, op: PairBy[_]): Either[ConfigureError, Differ[F[A]]] =
    op match {
      case PairBy.Index =>
        Right(
          new SeqDiffer[F, A](
            isIgnored = isIgnored,
            pairBy = PairBy.Index,
            itemDiffer = itemDiffer,
            typeName = typeName,
            tag = tag,
            itemTag = itemTag,
            asSeq = asSeq,
          ),
        )
      case m: PairBy.ByFunc[_, _] =>
        if (m.aTag == itemTag) {
          Right(
            new SeqDiffer[F, A](
              isIgnored = isIgnored,
              pairBy = m.asInstanceOf[ConfigureOp.PairBy[A]],
              itemDiffer = itemDiffer,
              typeName = typeName,
              tag = tag,
              itemTag = itemTag,
              asSeq = asSeq,
            ),
          )
        } else {
          Left(
            ConfigureError
              .TypeTagMismatch(path, obtainedTag = m.aTag.tag, expectedTag = itemTag.tag),
          )
        }
    }
}

object SeqDiffer {
  def create[F[_], A](
    itemDiffer: Differ[A],
    typeName: TypeName,
    asSeq: SeqLike[F],
  )(implicit tag: LTag[F[A]], itemTag: LTag[A]): SeqDiffer[F, A] = new SeqDiffer[F, A](
    isIgnored = false,
    pairBy = PairBy.Index,
    itemDiffer = itemDiffer,
    typeName = typeName,
    tag = tag,
    itemTag = itemTag,
    asSeq = asSeq,
  )

  // Given two lists of item, find "matching" items using te provided function
  // (where "matching" means ==). For example we might want to items by
  // person name.
  private[difflicious] def diffPairByFunc[A](
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

}

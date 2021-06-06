package difflicious.differ

import difflicious.ConfigureOp.PairBy
import difflicious.DiffResult.SetResult
import difflicious.differ.SeqDiffer.diffPairByFunc
import difflicious.utils.{TypeName, SetLike}
import izumi.reflect.macrortti.LTag
import difflicious.{PairType, Differ, ConfigureOp, ConfigureError, ConfigurePath, DiffInput}

// TODO: maybe find a way for stable ordering (i.e. only order on non-ignored fields)
final class SetDiffer[F[_], A](
  isIgnored: Boolean,
  itemDiffer: Differ[A],
  matchFunc: A => Any,
  typeName: TypeName,
  override protected val tag: LTag[F[A]],
  itemTag: LTag[A],
  asSet: SetLike[F],
) extends Differ[F[A]] {
  override type R = SetResult

  override def diff(inputs: DiffInput[F[A]]): R = inputs.map(asSet.asSet) match {
    case DiffInput.ObtainedOnly(actual) =>
      SetResult(
        typeName = typeName,
        actual.toVector.map { a =>
          itemDiffer.diff(DiffInput.ObtainedOnly(a))
        },
        PairType.ObtainedOnly,
        isIgnored = isIgnored,
        isOk = isIgnored,
      )
    case DiffInput.ExpectedOnly(expected) =>
      SetResult(
        typeName = typeName,
        items = expected.toVector.map { e =>
          itemDiffer.diff(DiffInput.ExpectedOnly(e))
        },
        pairType = PairType.ExpectedOnly,
        isIgnored = isIgnored,
        isOk = isIgnored,
      )
    case DiffInput.Both(obtained, expected) => {
      val (results, overallIsSame) = diffPairByFunc(obtained.toSeq, expected.toSeq, matchFunc, itemDiffer)
      SetResult(
        typeName = typeName,
        items = results,
        pairType = PairType.Both,
        isIgnored = isIgnored,
        isOk = isIgnored || overallIsSame,
      )
    }
  }

  override def configureIgnored(newIgnored: Boolean): Differ[F[A]] =
    new SetDiffer[F, A](
      isIgnored = newIgnored,
      itemDiffer = itemDiffer,
      matchFunc = matchFunc,
      typeName = typeName,
      tag = tag,
      itemTag = itemTag,
      asSet = asSet,
    )

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[F[A]]] =
    if (step == "each") {
      itemDiffer.configureRaw(nextPath, op).map { updatedItemDiffer =>
        new SetDiffer[F, A](
          isIgnored = isIgnored,
          itemDiffer = updatedItemDiffer,
          matchFunc = matchFunc,
          typeName = typeName,
          tag = tag,
          itemTag = itemTag,
          asSet = asSet,
        )
      }
    } else Left(ConfigureError.NonExistentField(nextPath, "SetDiffer"))

  override def configurePairBy(path: ConfigurePath, op: PairBy[_]): Either[ConfigureError, Differ[F[A]]] =
    op match {
      case PairBy.Index => Left(ConfigureError.InvalidConfigureOp(path, op, "SetDiffer"))
      case m: PairBy.ByFunc[_, _] =>
        if (m.aTag == itemTag) {
          Right(
            new SetDiffer[F, A](
              isIgnored = isIgnored,
              itemDiffer = itemDiffer,
              matchFunc = m.func.asInstanceOf[A => Any],
              typeName = typeName,
              tag = tag,
              itemTag = itemTag,
              asSet = asSet,
            ),
          )
        } else {
          Left(ConfigureError.TypeTagMismatch(path, m.aTag.tag, itemTag.tag))
        }
    }
}

object SetDiffer {
  def create[F[_], A](
    itemDiffer: Differ[A],
    typeName: TypeName,
    asSet: SetLike[F],
  )(
    implicit
    tag: LTag[F[A]],
    itemTag: LTag[A],
  ): SetDiffer[F, A] = new SetDiffer[F, A](
    isIgnored = false,
    itemDiffer,
    matchFunc = identity,
    typeName = typeName,
    tag = tag,
    itemTag = itemTag,
    asSet = asSet,
  )

}

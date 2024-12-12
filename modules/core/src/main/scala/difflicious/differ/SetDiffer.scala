package difflicious.differ

import difflicious.ConfigureOp.PairBy
import difflicious.DiffResult.ListResult
import difflicious.differ.SeqDiffer.diffPairByFunc
import difflicious.internal.SumCountsSyntax.DiffResultIterableOps
import difflicious.utils.TypeName.SomeTypeName
import difflicious.utils.SetLike
import difflicious.{ConfigureError, ConfigureOp, ConfigurePath, DiffInput, Differ, PairType, PairingFunction}

final class SetDiffer[F[_], A](
  isIgnored: Boolean,
  itemDiffer: Differ[A],
  matchFunc: PairingFunction[A, Any],
  typeName: SomeTypeName,
  asSet: SetLike[F],
) extends Differ[F[A]] {
  override type R = ListResult

  override def diff(inputs: DiffInput[F[A]]): R = inputs.map(asSet.asSet) match {
    case DiffInput.ObtainedOnly(actual) =>
      ListResult(
        typeName = typeName,
        actual.toVector.map { a =>
          itemDiffer.diff(DiffInput.ObtainedOnly(a))
        },
        PairType.ObtainedOnly,
        isIgnored = isIgnored,
        isOk = isIgnored,
        differenceCount = actual.size,
        ignoredCount = if (isIgnored) actual.size else 0,
      )
    case DiffInput.ExpectedOnly(expected) =>
      ListResult(
        typeName = typeName,
        items = expected.toVector.map { e =>
          itemDiffer.diff(DiffInput.ExpectedOnly(e))
        },
        pairType = PairType.ExpectedOnly,
        isIgnored = isIgnored,
        isOk = isIgnored,
        differenceCount = expected.size,
        ignoredCount = if (isIgnored) expected.size else 0,
      )
    case DiffInput.Both(obtained, expected) =>
      val (results, overallIsSame) = diffPairByFunc(obtained.toSeq, expected.toSeq, matchFunc, itemDiffer)
      ListResult(
        typeName = typeName,
        items = results,
        pairType = PairType.Both,
        isIgnored = isIgnored,
        isOk = isIgnored || overallIsSame,
        differenceCount = results.differenceCount,
        ignoredCount = results.ignoredCount,
      )
  }

  override def configureIgnored(newIgnored: Boolean): Differ[F[A]] =
    new SetDiffer[F, A](
      isIgnored = newIgnored,
      itemDiffer = itemDiffer,
      matchFunc = matchFunc,
      typeName = typeName,
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
          asSet = asSet,
        )
      }
    } else Left(ConfigureError.NonExistentField(nextPath, "SetDiffer"))

  override def configurePairBy(path: ConfigurePath, op: PairBy[_]): Either[ConfigureError, Differ[F[A]]] =
    op match {
      case PairBy.Index => Left(ConfigureError.InvalidConfigureOp(path, op, "SetDiffer"))
      case m: PairBy.ByFunc[_, _] =>
        Right(
          new SetDiffer[F, A](
            isIgnored = isIgnored,
            itemDiffer = itemDiffer,
            matchFunc = PairingFunction.lift(m.func.asInstanceOf[A => Any]),
            typeName = typeName,
            asSet = asSet,
          ),
        )
    }
}

object SetDiffer {
  def create[F[_], A](
    itemDiffer: Differ[A],
    typeName: SomeTypeName,
    asSet: SetLike[F],
  ): SetDiffer[F, A] = new SetDiffer[F, A](
    isIgnored = false,
    itemDiffer,
    matchFunc = PairingFunction.approximate(threshold = 1).asInstanceOf[PairingFunction[A, Any]],
    typeName = typeName,
    asSet = asSet,
  )

}

package difflicious
import difflicious.DiffResult.MismatchTypeResult
import difflicious.differ.RecordDiffer
import difflicious.utils.TypeName
import difflicious.utils.TypeName.SomeTypeName
import difflicious.internal.EitherGetSyntax._

import scala.collection.immutable.ListMap
import magnolia1._

import scala.collection.mutable

trait DifferGen extends Derivation[Differ]:
  override def join[T](ctx: CaseClass[Differ, T]): Differ[T] =
    new RecordDiffer[T](
    ctx.params.map { p =>
        val getter = p.deref
        p.label -> Tuple2(getter.asInstanceOf[(T => Any)], p.typeclass.asInstanceOf[Differ[Any]])
      }.to(ListMap),
      isIgnored = false,
      typeName = toDiffliciousTypeName(ctx.typeInfo)
    )

  override def split[T](ctx: SealedTrait[Differ, T]): Differ[T] =
    new SealedTraitDiffer(ctx, isIgnored = false)

  final class SealedTraitDiffer[T](ctx: SealedTrait[Differ, T], isIgnored: Boolean) extends Differ[T]:
    override type R = DiffResult

    override def diff(inputs: DiffInput[T]): DiffResult = inputs match
      case DiffInput.ObtainedOnly(obtained) =>
        ctx.choose(obtained)(sub => sub.typeclass.diff(DiffInput.ObtainedOnly(sub.cast(obtained))))
      case DiffInput.ExpectedOnly(expected) =>
        ctx.choose(expected)(sub => sub.typeclass.diff(DiffInput.ExpectedOnly(sub.cast(expected))))
      case DiffInput.Both(obtained, expected) =>
        ctx.choose(obtained) { obtainedSubtype =>
          ctx.choose(expected) { expectedSubtype =>
            if obtainedSubtype.typeInfo.short == expectedSubtype.typeInfo.short then
              obtainedSubtype.typeclass.asInstanceOf[Differ[T]].diff(obtainedSubtype.value, expectedSubtype.value)
            else MismatchTypeResult(
              obtained = obtainedSubtype.typeclass.diff(DiffInput.ObtainedOnly(obtainedSubtype.cast(obtained))),
              obtainedTypeName = toDiffliciousTypeName(obtainedSubtype.typeInfo),
              expected = expectedSubtype.typeclass.diff(DiffInput.ExpectedOnly(expectedSubtype.cast(expected))),
              expectedTypeName = toDiffliciousTypeName(expectedSubtype.typeInfo),
              pairType = PairType.Both,
              isIgnored = isIgnored,
            )
          }
        }


    override def configureIgnored(newIgnored: Boolean): Differ[T] =
      val newSubtypes = mutable.ArrayBuffer.empty[SealedTrait.Subtype[Differ, T, Any]]
      ctx.subtypes.foreach { sub =>
        newSubtypes += SealedTrait.Subtype[Differ, T, Any](
          typeInfo = sub.typeInfo,
          annotations = sub.annotations,
          typeAnnotations = sub.typeAnnotations,
          isObject = sub.isObject,
          index = sub.index,
          callByNeed =
            CallByNeed(sub.typeclass.configureRaw(ConfigurePath.current, ConfigureOp.SetIgnored(newIgnored)).unsafeGet.asInstanceOf[Differ[Any]]),
          isType = sub.cast.isDefinedAt,
          asType = sub.cast.apply,
        )
      }
      val newSealedTrait = new SealedTrait(
        typeInfo = ctx.typeInfo,
        subtypes = IArray(newSubtypes.toArray: _*),
        annotations = ctx.annotations,
        typeAnnotations = ctx.typeAnnotations,
        isEnum = ctx.isEnum,
      )
      new SealedTraitDiffer[T](newSealedTrait, isIgnored = newIgnored)

    protected def configurePath(
      step: String,
      nextPath: ConfigurePath,
      op: ConfigureOp
    ): Either[ConfigureError, Differ[T]] =
      ctx.subtypes.zipWithIndex.find{ (sub, _) => sub.typeInfo.short == step} match {
        case Some((sub, idx)) =>
          sub.typeclass
            .configureRaw(nextPath, op)
            .map { newDiffer =>
              val newSubtype = SealedTrait.Subtype[Differ, T, Any](
                typeInfo = sub.typeInfo,
                annotations = sub.annotations,
                typeAnnotations = sub.typeAnnotations,
                isObject = sub.isObject,
                index = sub.index,
                callByNeed = CallByNeed(newDiffer.asInstanceOf[Differ[Any]]),
                isType = sub.cast.isDefinedAt,
                asType = sub.cast.apply,
              )
              val newSubtypes = ctx.subtypes.updated(idx, newSubtype)
              val newSealedTrait = new SealedTrait(
                typeInfo = ctx.typeInfo,
                subtypes = newSubtypes,
                annotations = ctx.annotations,
                typeAnnotations = ctx.typeAnnotations,
                isEnum = ctx.isEnum,
              )
              new SealedTraitDiffer[T](newSealedTrait, isIgnored)
            }
        case None =>
          Left(ConfigureError.UnrecognizedSubType(nextPath, ctx.subtypes.map(_.typeInfo.short).toVector))
      }

    protected def configurePairBy(path: ConfigurePath, op: ConfigureOp.PairBy[_]): Either[ConfigureError, Differ[T]] =
      Left(ConfigureError.InvalidConfigureOp(path, op, "SealedTraitDiffer"))

  end SealedTraitDiffer

  private def toDiffliciousTypeName(typeInfo: TypeInfo): SomeTypeName = {
    TypeName(
      long = s"${typeInfo.owner}.${typeInfo.short}",
      short = typeInfo.short,
      typeArguments = typeInfo.typeParams.map(toDiffliciousTypeName).toList
    )
  }

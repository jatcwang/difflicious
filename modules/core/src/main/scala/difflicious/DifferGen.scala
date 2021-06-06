package difflicious
import difflicious.DiffResult.MismatchTypeResult
import difflicious.differ.RecordDiffer
import difflicious.internal.EitherGetSyntax._
import izumi.reflect.macrortti.LTag
import magnolia._
import difflicious.utils.{TypeName => DTypeName}

import scala.collection.mutable
import scala.collection.immutable.ListMap

trait DifferGen {
  type Typeclass[T] = Differ[T]

  def combine[T](ctx: ReadOnlyCaseClass[Differ, T])(implicit tag: LTag[T]): Differ[T] = {
    new RecordDiffer[T](
      ctx.parameters
        .map { p =>
          val getter = p.dereference _
          p.label -> Tuple2(getter.asInstanceOf[(T => Any)], p.typeclass.asInstanceOf[Differ[Any]])
        }
        .to(ListMap),
      isIgnored = false,
      tag = tag,
      typeName = DTypeName.fromLightTypeTag(tag.tag),
    )
  }

  final class SealedTraitDiffer[T](ctx: SealedTrait[Differ, T], isIgnored: Boolean, override protected val tag: LTag[T])
      extends Differ[T] {
    // $COVERAGE-OFF$
    require(
      {
        val allShortNames = ctx.subtypes.map(_.typeName.short)
        allShortNames.toSet.size == allShortNames.size
      },
      "Currently all subclass names across the whole sealed trait hierarchy must be distinct for simplicity of the configure API. " +
        "Please raise an issue if you need subclasses with the same names",
    )
    // $COVERAGE-ON$

    override type R = DiffResult

    override def diff(inputs: DiffInput[T]): DiffResult = inputs match {
      case DiffInput.ObtainedOnly(obtained) =>
        ctx.dispatch(obtained)(sub => sub.typeclass.diff(DiffInput.ObtainedOnly(sub.cast(obtained))))
      case DiffInput.ExpectedOnly(expected) =>
        ctx.dispatch(expected)(sub => sub.typeclass.diff(DiffInput.ExpectedOnly(sub.cast(expected))))
      case DiffInput.Both(obtained, expected) => {
        ctx.dispatch(obtained) { actualSubtype =>
          ctx.dispatch(expected) { expectedSubtype =>
            if (actualSubtype.typeName.short == expectedSubtype.typeName.short) {
              actualSubtype.typeclass
                .diff(actualSubtype.cast(obtained), expectedSubtype.cast(expected).asInstanceOf[actualSubtype.SType])
            } else {
              MismatchTypeResult(
                obtained = actualSubtype.typeclass.diff(DiffInput.ObtainedOnly(actualSubtype.cast(obtained))),
                obtainedTypeName = toDiffliciousTypeName(actualSubtype.typeName),
                expected = expectedSubtype.typeclass.diff(DiffInput.ExpectedOnly(expectedSubtype.cast(expected))),
                expectedTypeName = toDiffliciousTypeName(expectedSubtype.typeName),
                pairType = PairType.Both,
                isIgnored = isIgnored,
              )
            }
          }
        }
      }
    }

    override def configureIgnored(newIgnored: Boolean): Typeclass[T] = {
      val newSubTypes = mutable.ArrayBuffer.empty[Subtype[Differ, T]]
      ctx.subtypes.map { sub =>
        newSubTypes += Subtype(
          name = sub.typeName,
          idx = sub.index,
          anns = sub.annotationsArray,
          tpeAnns = sub.typeAnnotationsArray,
          tc =
            CallByNeed(sub.typeclass.configureRaw(ConfigurePath.current, ConfigureOp.SetIgnored(newIgnored)).unsafeGet),
          isType = sub.cast.isDefinedAt,
          asType = sub.cast.apply,
        )
      }
      val newSealedTrait = new SealedTrait(
        typeName = ctx.typeName,
        subtypesArray = newSubTypes.toArray,
        annotationsArray = ctx.annotations.toArray,
        typeAnnotationsArray = ctx.typeAnnotations.toArray,
      )
      new SealedTraitDiffer[T](newSealedTrait, isIgnored = newIgnored, tag = tag)
    }

    override def configurePath(
      step: String,
      nextPath: ConfigurePath,
      op: ConfigureOp,
    ): Either[ConfigureError, Typeclass[T]] =
      ctx.subtypes.zipWithIndex.find { case (sub, _) => sub.typeName.short == step } match {
        case Some((sub, idx)) =>
          sub.typeclass
            .configureRaw(nextPath, op)
            .map { newDiffer =>
              Subtype(
                name = sub.typeName,
                idx = sub.index,
                anns = sub.annotationsArray,
                tpeAnns = sub.typeAnnotationsArray,
                tc = CallByNeed(newDiffer),
                isType = sub.cast.isDefinedAt,
                asType = sub.cast.apply,
              )
            }
            .map { newSubType =>
              val newSubTypes = ctx.subtypes.updated(idx, newSubType)
              val newSealedTrait = new SealedTrait(
                typeName = ctx.typeName,
                subtypesArray = newSubTypes.toArray,
                annotationsArray = ctx.annotations.toArray,
                typeAnnotationsArray = ctx.typeAnnotations.toArray,
              )
              new SealedTraitDiffer[T](newSealedTrait, isIgnored, tag = tag)
            }
        case None =>
          Left(ConfigureError.UnrecognizedSubType(nextPath, ctx.subtypes.map(_.typeName.short).toVector))
      }

    override def configurePairBy(path: ConfigurePath, op: ConfigureOp.PairBy[_]): Either[ConfigureError, Typeclass[T]] =
      Left(ConfigureError.InvalidConfigureOp(path, op, "SealedTraitDiffer"))
  }

  def dispatch[T](ctx: SealedTrait[Differ, T])(implicit tag: LTag[T]): Differ[T] =
    new SealedTraitDiffer[T](ctx, isIgnored = false, tag = tag)

  def derive[T]: Differ[T] = macro Magnolia.gen[T]

  private def toDiffliciousTypeName(typeName: magnolia.TypeName): difflicious.utils.TypeName = {
    DTypeName(
      long = typeName.full,
      short = typeName.short,
      typeArguments = typeName.typeArguments
        .map(
          // $COVERAGE-OFF$ Type params aren't printed when type mismatches
          toDiffliciousTypeName,
          // $COVERAGE-ON$
        )
        .toList,
    )
  }
}

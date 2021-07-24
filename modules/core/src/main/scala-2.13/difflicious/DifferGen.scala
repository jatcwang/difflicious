package difflicious
import difflicious.DiffResult.MismatchTypeResult
import difflicious.differ.RecordDiffer
import difflicious.internal.EitherGetSyntax._
import difflicious.utils.TypeName.SomeTypeName
import magnolia._

import scala.collection.mutable
import scala.collection.immutable.ListMap

trait DifferGen {
  type Typeclass[T] = Differ[T]

  def combine[T](ctx: ReadOnlyCaseClass[Differ, T]): Differ[T] = {
    new RecordDiffer[T](
      ctx.parameters
        .map { p =>
          val getter = p.dereference _
          p.label -> Tuple2(getter.asInstanceOf[(T => Any)], p.typeclass.asInstanceOf[Differ[Any]])
        }
        .to(ListMap),
      isIgnored = false,
      typeName = toDiffliciousTypeName(ctx.typeName),
    )
  }

  final class SealedTraitDiffer[T](ctx: SealedTrait[Differ, T], isIgnored: Boolean) extends Differ[T] {
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
        ctx.dispatch(obtained) { obtainedSubtype =>
          ctx.dispatch(expected) { expectedSubtype =>
            if (obtainedSubtype.typeName.short == expectedSubtype.typeName.short) {
              obtainedSubtype.typeclass
                .diff(
                  obtainedSubtype.cast(obtained),
                  expectedSubtype.cast(expected).asInstanceOf[obtainedSubtype.SType]
                )
            } else {
              MismatchTypeResult(
                obtained = obtainedSubtype.typeclass.diff(DiffInput.ObtainedOnly(obtainedSubtype.cast(obtained))),
                obtainedTypeName = toDiffliciousTypeName(obtainedSubtype.typeName),
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
      new SealedTraitDiffer[T](newSealedTrait, isIgnored = newIgnored)
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
              new SealedTraitDiffer[T](newSealedTrait, isIgnored)
            }
        case None =>
          Left(ConfigureError.UnrecognizedSubType(nextPath, ctx.subtypes.map(_.typeName.short).toVector))
      }

    override def configurePairBy(path: ConfigurePath, op: ConfigureOp.PairBy[_]): Either[ConfigureError, Typeclass[T]] =
      Left(ConfigureError.InvalidConfigureOp(path, op, "SealedTraitDiffer"))

  }

  def dispatch[T](ctx: SealedTrait[Differ, T]): Differ[T] =
    new SealedTraitDiffer[T](ctx, isIgnored = false)

  def derived[T]: Differ[T] = macro Magnolia.gen[T]

  private def toDiffliciousTypeName(typeName: magnolia.TypeName): SomeTypeName = {
    difflicious.utils.TypeName(
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

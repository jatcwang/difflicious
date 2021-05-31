package difflicious
import cats.data.Ior
import difflicious.DiffResult.MismatchTypeResult
import difflicious.differ.RecordDiffer
import izumi.reflect.macrortti.LTag
import magnolia._
import difflicious.utils.{TypeName => DTypeName}

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
      typeName = DTypeName.fromLightTypeTag(tag.tag),
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
        "Please raise an issue if you need duplicate subclass names",
    )
    // $COVERAGE-ON$

    override type R = DiffResult

    override def diff(inputs: Ior[T, T]): DiffResult = inputs match {
      case Ior.Left(obtained)  => ctx.dispatch(obtained)(sub => sub.typeclass.diff(Ior.Left(sub.cast(obtained))))
      case Ior.Right(expected) => ctx.dispatch(expected)(sub => sub.typeclass.diff(Ior.Right(sub.cast(expected))))
      case Ior.Both(obtained, expected) => {
        ctx.dispatch(obtained) { actualSubtype =>
          ctx.dispatch(expected) { expectedSubtype =>
            if (actualSubtype.typeName.short == expectedSubtype.typeName.short) {
              actualSubtype.typeclass
                .diff(actualSubtype.cast(obtained), expectedSubtype.cast(expected).asInstanceOf[actualSubtype.SType])
            } else {
              MismatchTypeResult(
                obtained = actualSubtype.typeclass.diff(Ior.Left(actualSubtype.cast(obtained))),
                obtainedTypeName = toDiffliciousTypeName(actualSubtype.typeName),
                expected = expectedSubtype.typeclass.diff(Ior.Right(expectedSubtype.cast(expected))),
                expectedTypeName = toDiffliciousTypeName(expectedSubtype.typeName),
                matchType = MatchType.Both,
                isIgnored = isIgnored,
              )
            }
          }
        }
      }
    }

    // FIXME: test: two layers of sealed trait. Will probably need a "stack" of subtype name in the API response?
    override def configureRaw(path: ConfigurePath, op: ConfigureOp): Either[DifferUpdateError, Typeclass[T]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(shortName) =>
          ctx.subtypes.zipWithIndex.find { case (sub, _) => sub.typeName.short == shortName } match {
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
              Left(DifferUpdateError.UnrecognizedSubType(nextPath, ctx.subtypes.map(_.typeName.short).toVector))
          }
        case None =>
          op match {
            case ignoreOp @ ConfigureOp.SetIgnored(newIgnored) => {
              import cats.implicits._
              ctx.subtypes.toList
                .traverse { sub =>
                  sub.typeclass.configureRaw(path, ignoreOp).map { newDiffer =>
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
                }
                .map { newSubTypes =>
                  val newSealedTrait = new SealedTrait(
                    typeName = ctx.typeName,
                    subtypesArray = newSubTypes.toArray,
                    annotationsArray = ctx.annotations.toArray,
                    typeAnnotationsArray = ctx.typeAnnotations.toArray,
                  )
                  new SealedTraitDiffer[T](newSealedTrait, isIgnored = newIgnored)
                }
            }
            case _: ConfigureOp.PairBy[_] => Left(DifferUpdateError.InvalidDifferOp(nextPath, op, "sealed trait"))
          }

      }
    }

  }

  def dispatch[T](ctx: SealedTrait[Differ, T]): Differ[T] =
    new SealedTraitDiffer[T](ctx, isIgnored = false)

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

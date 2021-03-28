package difflicious
import cats.data.Ior
import difflicious.DiffResult.MismatchTypeResult
import difflicious.Differ.RecordDiffer

import magnolia._

import scala.collection.immutable.ListMap

object DiffGen {
  type Typeclass[T] = Differ[T]

  def combine[T](ctx: ReadOnlyCaseClass[Differ, T]): Differ[T] = {
    new RecordDiffer[T](
      ctx.parameters
        .map { p =>
          val getter = p.dereference _
          p.label -> Tuple2(getter.asInstanceOf[(T => Any)], p.typeclass.asInstanceOf[Differ[Any]])
        }
        .to(ListMap),
      ignored = false,
    )
  }

  class SealedTraitDiffer[T](ctx: SealedTrait[Differ, T], ignored: Boolean) extends Differ[T] {
    override def diff(inputs: Ior[T, T]): DiffResult = inputs match {
      case Ior.Left(actual)    => ctx.dispatch(actual)(sub => sub.typeclass.diff(Ior.Left(sub.cast(actual))))
      case Ior.Right(expected) => ctx.dispatch(expected)(sub => sub.typeclass.diff(Ior.Right(sub.cast(expected))))
      case Ior.Both(actual, expected) => {
        ctx.dispatch(actual) { actualSubtype =>
          ctx.dispatch(expected) { expectedSubtype =>
            if (actualSubtype.typeName.short == expectedSubtype.typeName.short) {
              actualSubtype.typeclass
                .diff(actualSubtype.cast(actual), expectedSubtype.cast(expected).asInstanceOf[actualSubtype.SType])
            } else {
              MismatchTypeResult(
                actualSubtype.typeclass.diff(Ior.Left(actualSubtype.cast(actual))),
                expectedSubtype.typeclass.diff(Ior.Right(expectedSubtype.cast(expected))),
                matchType = MatchType.Both,
                ignored = false,
              )
            }
          }
        }
      }
    }

    override def updateIgnore(path: IgnorePath, newIgnored: Boolean): Either[IgnoreError, Typeclass[T]] = {
      path.next match {
        case (Some(IgnoreStep.Down(fullTypeName)), nextPath) =>
          ctx.subtypes.zipWithIndex.find { case (sub, _) => sub.typeName.full == fullTypeName } match {
            case Some((sub, idx)) =>
              sub.typeclass
                .updateIgnore(nextPath, newIgnored)
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
                  new SealedTraitDiffer[T](newSealedTrait, ignored)
                }
            case None =>
              Left(IgnoreError.InvalidSubType(nextPath.resolvedSteps, ctx.subtypes.map(_.typeName.full).toVector))
          }
        case (Some(_), nextPath) => Left(IgnoreError.UnexpectedDifferType(nextPath.resolvedSteps, "sealed trait"))
        case (None, _)           => Right(new SealedTraitDiffer[T](ctx, ignored = newIgnored))
      }
    }
  }

  def dispatch[T](ctx: SealedTrait[Differ, T]): Differ[T] =
    new SealedTraitDiffer[T](ctx, ignored = false)

  def derive[T]: Differ[T] = macro Magnolia.gen[T]
}

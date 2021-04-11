package difflicious
import cats.data.Ior
import difflicious.DiffResult.MismatchTypeResult
import difflicious.Differ.RecordDiffer
import izumi.reflect.Tag
import magnolia._
import difflicious.utils.{TypeName => DTypeName}

import scala.collection.immutable.ListMap

object DiffGen {
  type Typeclass[T] = Differ[T]

  def combine[T](ctx: ReadOnlyCaseClass[Differ, T])(implicit tag: Tag[T]): Differ[T] = {
    new RecordDiffer[T](
      ctx.parameters
        .map { p =>
          val getter = p.dereference _
          p.label -> Tuple2(getter.asInstanceOf[(T => Any)], p.typeclass.asInstanceOf[Differ[Any]])
        }
        .to(ListMap),
      ignored = false,
      tag = tag,
    )
  }

  final class SealedTraitDiffer[T](ctx: SealedTrait[Differ, T], isIgnored: Boolean) extends Differ[T] {
    override type R = DiffResult

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
                actual = actualSubtype.typeclass.diff(Ior.Left(actualSubtype.cast(actual))),
                actualTypeName = toDiffliciousTypeName(actualSubtype.typeName),
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
    override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, Typeclass[T]] = {
      val (step, nextPath) = path.next
      step match {
        case Some(UpdateStep.DownSubtype(fullTypeName)) =>
          ctx.subtypes.zipWithIndex.find { case (sub, _) => sub.typeName.full == fullTypeName } match {
            case Some((sub, idx)) =>
              sub.typeclass
                .updateWith(nextPath, op)
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
              Left(DifferUpdateError.InvalidSubType(nextPath, ctx.subtypes.map(_.typeName.full).toVector))
          }
        case Some(_) => Left(DifferUpdateError.UnexpectedDifferType(nextPath, "sealed trait"))
        case None =>
          op match {
            case DifferOp.SetIgnored(newIgnored) =>
              Right(new SealedTraitDiffer[T](ctx, isIgnored = newIgnored))
            case _: DifferOp.MatchBy[_] => Left(DifferUpdateError.InvalidDifferOp(nextPath, op, "record"))
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
      typeArguments = typeName.typeArguments.map(toDiffliciousTypeName).toVector,
    )
  }
}

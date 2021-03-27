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
    )
  }

  def dispatch[T](ctx: SealedTrait[Differ, T]): Differ[T] =
    new Differ[T] {
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
                )
              }
            }
          }
        }
      }
    }

  def derive[T]: Differ[T] = macro Magnolia.gen[T]
}

package difflicious
import difflicious.differ.{OneOfDiffer, RecordDiffer}
import difflicious.utils.TypeName.SomeTypeName
import magnolia1.*

import scala.collection.immutable.ListMap

trait DifferGen {
  type Typeclass[T] = Differ[T]

  def join[T](ctx: ReadOnlyCaseClass[Differ, T]): Differ[T] = {
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

  def split[T](ctx: SealedTrait[Differ, T]): Differ[T] =
    new OneOfDiffer[T](
      cases = ctx.subtypes.map { sub =>
        val extract: T => Option[Any] = value =>
          if (sub.cast.isDefinedAt(value)) Some(sub.cast(value))
          else None
        OneOfDiffer.Case[T, Any](
          typeName = toDiffliciousTypeName(sub.typeName),
          extract = extract,
          differ = sub.typeclass.asInstanceOf[Differ[Any]],
        )
      }.toVector,
      isIgnored = false,
      differTypeName = "OneOfDiffer",
    )

  def derived[T]: Differ[T] = macro Magnolia.gen[T]

  private def toDiffliciousTypeName(typeName: magnolia1.TypeName): SomeTypeName = {
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

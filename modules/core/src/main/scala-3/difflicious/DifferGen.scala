package difflicious
import difflicious.differ.{OneOfDiffer, RecordDiffer}
import difflicious.utils.TypeName
import difflicious.utils.TypeName.SomeTypeName

import scala.collection.immutable.ListMap
import magnolia1.*

trait DifferGen extends Derivation[Differ]:
  override def join[T](ctx: CaseClass[Differ, T]): Differ[T] =
    new RecordDiffer[T](
      ctx.params
        .map { p =>
          val getter = p.deref
          p.label -> Tuple2(getter.asInstanceOf[(T => Any)], p.typeclass.asInstanceOf[Differ[Any]])
        }
        .to(ListMap),
      isIgnored = false,
      typeName = toDiffliciousTypeName(ctx.typeInfo),
    )

  override def split[T](ctx: SealedTrait[Differ, T]): Differ[T] =
    new OneOfDiffer[T](
      cases = ctx.subtypes.iterator.map { sub =>
        val extract: T => Option[Any] = value =>
          if sub.cast.isDefinedAt(value) then Some(sub.cast(value))
          else None
        OneOfDiffer.Case[T, Any](
          typeName = toDiffliciousTypeName(sub.typeInfo),
          extract = extract,
          differ = sub.typeclass.asInstanceOf[Differ[Any]],
        )
      }.toVector,
      isIgnored = false,
      differTypeName = "OneOfDiffer",
    )

  private def toDiffliciousTypeName(typeInfo: TypeInfo): SomeTypeName = {
    TypeName(
      long = s"${typeInfo.owner}.${typeInfo.short}",
      short = typeInfo.short,
      typeArguments = typeInfo.typeParams.map(toDiffliciousTypeName).toList,
    )
  }

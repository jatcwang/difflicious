package difflicious.utils

import izumi.reflect.macrortti.{LightTypeTag, LTag}

final case class TypeName[A](long: String, short: String, typeArguments: List[TypeName[_]]) {
  def withTypeParamsLong: String = {
    s"$long${typeArguments.map(_.withTypeParamsLong).mkString("[", ",", "]")}"
  }
}

object TypeName extends TypeNamePlatform {

  // A type name without a compile time type known.
  type SomeTypeName = TypeName[_]

  implicit def apply[A](implicit tag: LTag[A]): TypeName[A] = {
    fromLightTypeTag(tag.tag)
  }

  def fromLightTypeTag[A](t: LightTypeTag): TypeName[A] = {
    TypeName[A](
      long = unescapeIdentifierName(t.longName),
      short = unescapeIdentifierName(t.shortName),
      typeArguments = t.typeArgs.map(fromLightTypeTag),
    )
  }
}

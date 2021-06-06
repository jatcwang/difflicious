package difflicious.utils

import izumi.reflect.macrortti.LightTypeTag

final case class TypeName(long: String, short: String, typeArguments: List[TypeName]) {
  def withTypeParamsLong: String = {
    s"$long${typeArguments.map(_.withTypeParamsLong).mkString("[", ",", "]")}"
  }
}

object TypeName extends TypeNamePlatform {
  def fromLightTypeTag(t: LightTypeTag): TypeName = {
    TypeName(
      long = unescapeIdentifierName(t.longName),
      short = unescapeIdentifierName(t.shortName),
      typeArguments = t.typeArgs.map(fromLightTypeTag),
    )
  }
}

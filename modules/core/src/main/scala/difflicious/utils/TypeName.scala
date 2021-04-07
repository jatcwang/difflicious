package difflicious.utils

import izumi.reflect.macrortti.LightTypeTag

final case class TypeName(long: String, short: String, typeArguments: Vector[TypeName]) {
  def withTypeParamsLong: String = {
    s"$long${typeArguments.map(_.withTypeParamsLong).mkString("[", ",", "]")}"
  }
}

object TypeName {
  def fromTag(t: LightTypeTag): TypeName = {
    TypeName(
      long = t.longName,
      short = t.shortName,
      typeArguments = t.typeArgs.map(fromTag).toVector,
    )
  }
}

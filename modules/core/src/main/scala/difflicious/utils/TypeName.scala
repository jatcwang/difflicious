package difflicious.utils

final case class TypeName[A](long: String, short: String, typeArguments: List[TypeName[?]]) {
  def withTypeParamsLong: String = {
    if (typeArguments.isEmpty) long
    else s"$long${typeArguments.map(_.withTypeParamsLong).mkString("[", ",", "]")}"
  }
}

object TypeName extends TypeNamePlatform {

  // A type name without a compile time type known.
  type SomeTypeName = TypeName[?]
}

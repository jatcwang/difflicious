package difflicious.utils

trait TypeNamePlatform {

  def unescapeIdentifierName(str: String): String = {
    scala.reflect.runtime.universe.TypeName(str).decodedName.toString
  }

}

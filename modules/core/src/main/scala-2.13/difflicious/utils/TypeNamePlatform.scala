package difflicious.utils

import scala.reflect.macros.blackbox

trait TypeNamePlatform {
  def apply[A]: TypeName[A] = macro TypeNameMacros.applyImpl[A]

  implicit def derived[A]: TypeName[A] = macro TypeNameMacros.applyImpl[A]
}

private[difflicious] object TypeNameMacros {
  def applyImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeName[A]] = {
    import c.universe._

    c.Expr[difflicious.utils.TypeName[A]](typeNameTree(c)(weakTypeOf[A], tq"${weakTypeOf[A]}"))
  }

  private def typeNameTree(c: blackbox.Context)(
    tpe: c.universe.Type,
    resultTpt: c.universe.Tree,
  ): c.universe.Tree = {
    import c.universe._

    val dealiased = tpe.dealias
    val symbol = typeSymbol(c)(dealiased)
    val long = decodedName(symbol.fold(dealiased.toString)(_.fullName))
    val short = decodedName(symbol.fold(dealiased.toString)(_.name.toString))
    val typeArguments = dealiased.typeArgs.map { arg =>
      typeNameTree(c)(arg, tq"_root_.scala.Any")
    }
    val typeArgumentsTree = q"_root_.scala.List[_root_.difflicious.utils.TypeName[_]](..$typeArguments)"

    q"_root_.difflicious.utils.TypeName[$resultTpt]($long, $short, $typeArgumentsTree)"
  }

  private def typeSymbol(c: blackbox.Context)(tpe: c.universe.Type): Option[c.universe.Symbol] = {
    import c.universe._

    Option(tpe.typeSymbol).filter(_ != NoSymbol)
  }

  private def decodedName(value: String): String =
    scala.reflect.NameTransformer
      .decode(value)
      .replace("$.", ".")
      .stripSuffix("$")
      .stripSuffix(".type")
}

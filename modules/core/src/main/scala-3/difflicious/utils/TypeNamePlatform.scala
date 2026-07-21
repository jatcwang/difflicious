package difflicious.utils

import scala.quoted.{Expr, Quotes, Type}

trait TypeNamePlatform {
  inline def apply[A]: TypeName[A] = ${ TypeNameMacros.applyImpl[A] }

  inline implicit def derived[A]: TypeName[A] = apply[A]
}

private[difflicious] object TypeNameMacros {
  def applyImpl[A: Type](using q: Quotes): Expr[TypeName[A]] =
    typeNameExpr[A]

  private def typeNameExpr[A: Type](using q: Quotes): Expr[TypeName[A]] = {
    import q.reflect.*

    def typeNameParts(tpe: TypeRepr): (String, String, List[TypeRepr]) = {
      val originalSymbol = typeSymbol(tpe)
      val dealiased =
        if (originalSymbol != Symbol.noSymbol && originalSymbol.flags.is(Flags.Opaque)) tpe
        else tpe.dealias
      val symbol = typeSymbol(dealiased)
      val long = decodedName(if (symbol == Symbol.noSymbol) dealiased.show else symbol.fullName)
      val short = decodedName(if (symbol == Symbol.noSymbol) dealiased.show else symbol.name)
      val typeArguments = dealiased match {
        case AppliedType(_, args) => args
        case _ => Nil
      }

      (long, short, typeArguments)
    }

    def typeNameExprOf(tpe: TypeRepr): Expr[TypeName[?]] = {
      val (long, short, typeArguments) = typeNameParts(tpe)
      val typeArgumentsExpr = Expr.ofList(typeArguments.map(typeNameExprOf))

      '{ TypeName[Any](${ Expr(long) }, ${ Expr(short) }, $typeArgumentsExpr) }
    }

    def typeNameExprFor[T: Type](tpe: TypeRepr): Expr[TypeName[T]] = {
      val (long, short, typeArguments) = typeNameParts(tpe)
      val typeArgumentsExpr = Expr.ofList(typeArguments.map(typeNameExprOf))

      '{ TypeName[T](${ Expr(long) }, ${ Expr(short) }, $typeArgumentsExpr) }
    }

    typeNameExprFor[A](TypeRepr.of[A])
  }

  private def decodedName(value: String): String =
    scala.reflect.NameTransformer
      .decode(value)
      .replace("$.", ".")
      .stripSuffix("$")
      .stripSuffix(".type")

  private def typeSymbol(using q: Quotes)(tpe: q.reflect.TypeRepr): q.reflect.Symbol = {
    import q.reflect.*

    val symbol = tpe.typeSymbol
    if (symbol != Symbol.noSymbol) symbol
    else tpe.termSymbol
  }
}

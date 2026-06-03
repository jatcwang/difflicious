package difflicious.testutils

import scala.quoted.*

object ExpandedTree:

  inline def expandTreeAndSimplify[A](inline value: A): String = ${ expandTreeAndSimplifyImpl('value) }

  def expandTreeAndSimplifyImpl[A](value: Expr[A])(using q: Quotes): Expr[String] =
    import q.reflect.*

    Expr(TreeRendering.simplify(value.asTerm.show(using Printer.TreeCode)))

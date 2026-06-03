package difflicious.testutils

import scala.reflect.macros.whitebox

object ExpandedTree {

  def expandTreeAndSimplify[A](value: A): String = macro expandTreeAndSimplifyImpl[A]

  def expandTreeAndSimplifyImpl[A](c: whitebox.Context)(value: c.Expr[A]): c.Expr[String] = {
    import c.universe._

    c.Expr[String](Literal(Constant(TreeRendering.simplify(showCode(value.tree)))))
  }
}

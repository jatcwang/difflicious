package difflicious.generic

import difflicious.Derived
import magnolia1.Magnolia

private[generic] object DerivedGen {

  import scala.reflect.macros.whitebox

  def derivedGen[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Derived[T]] = {
    import c.universe._
    c.Expr[Derived[T]](q"difflicious.Derived(${Magnolia.gen[T](c)(implicitly[c.WeakTypeTag[T]])})")
  }
}

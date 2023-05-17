package difflicious.generic

import difflicious.Derived
import difflicious.DifferGen
import difflicious.Differ
import magnolia1._
import scala.deriving.Mirror

package object auto extends DerivedAutoDerivation

trait DerivedAutoDerivation extends AutoDerivation[Differ] with DifferGen {

  given derivedDiff[T]: Conversion[Differ[T], Derived[T]] = d => Derived(d)

  inline implicit def diffForCaseClass[T](implicit m: Mirror.Of[T]): Derived[T] = Derived(derived[T])
}

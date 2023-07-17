package difflicious.generic

import difflicious.Derived
import difflicious.DifferGen
import difflicious.Differ

package object auto extends AutoDerivation

trait AutoDerivation extends DifferGen {

  implicit def derivedDiff[T](implicit dd: Derived[T]): Differ[T] = dd.differ

  implicit def diffForCaseClass[T]: Derived[T] = macro DerivedGen.derivedGen[T]

  def fallback[T]: Differ[T] = Differ.useEquals[T](_.toString)
}

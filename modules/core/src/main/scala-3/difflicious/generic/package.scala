package difflicious.generic

import difflicious.{Differ, DifferGen, DifferMacros}

package object auto extends AutoDerivation

trait AutoDerivation extends DifferGen {

  inline given autoDerivedDiffer[T]: Differ[T] = ${ DifferMacros.deriveAutoImpl[T] }

  def fallback[T]: Differ[T] = Differ.useEquals[T](_.toString)
}

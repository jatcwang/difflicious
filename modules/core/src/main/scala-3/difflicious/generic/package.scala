package difflicious.generic

import difflicious.{Differ, DifferGen, DifferDerivationMacros}

package object auto extends AutoDerivation

trait AutoDerivation extends DifferGen {

  inline given autoDerivedDiffer[T]: Differ[T] = ${ DifferDerivationMacros.deriveDeepImpl[T] }

  def fallback[T]: Differ[T] = Differ.useEquals[T](_.toString)
}

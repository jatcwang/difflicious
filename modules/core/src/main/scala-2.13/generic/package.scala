package difflicious.generic

import difflicious.DifferGen
import difflicious.Differ
import difflicious.{DifferMacros => DM}

package object auto extends AutoDerivation

trait AutoDerivation extends DifferGen {

  implicit def autoDerivedDiffer[T]: Differ[T] = macro DM.autoImpl[T]

  def fallback[T]: Differ[T] = Differ.useEquals[T](_.toString)
}

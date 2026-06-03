package difflicious

import scala.compiletime.summonFrom

object DifferDeepSummon:

  transparent inline def summonOrDerive[A]: Differ[A] =
    summonFrom {
      case differ: Differ[A] => differ
      case _ => difflicious.Differ.derivedDeep[A]
    }

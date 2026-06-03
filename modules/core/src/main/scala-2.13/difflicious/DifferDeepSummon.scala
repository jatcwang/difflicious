package difflicious

object DifferDeepSummon {

  def summonOrDerive[A]: Differ[A] = macro DifferMacros.summonOrDeriveImpl[A]
}

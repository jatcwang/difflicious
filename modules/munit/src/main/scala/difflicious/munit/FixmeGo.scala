package difflicious.munit

import difflicious.Differ

object FixmeGo {
  def main(args: Array[String]): Unit = {
    import MUnitDiff._

    Boo.d.assertDiff(Boo(1, 2), Boo(1, 3))
  }

  case class Boo(i: Int, d: Double)

  object Boo {
    val d: Differ[Boo] = Differ.derive[Boo]
  }
}

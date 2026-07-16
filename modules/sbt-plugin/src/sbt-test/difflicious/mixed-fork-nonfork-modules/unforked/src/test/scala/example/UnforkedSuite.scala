package example

import difflicious.Differ
import difflicious.scalatest.ScalatestDiff.*
import org.scalatest.funsuite.AnyFunSuite

class UnforkedSuite extends AnyFunSuite {
  test("reports diff result") {
    Differ.useEquals[Int](_.toString).assertNoDiff(1, 2)
  }
}

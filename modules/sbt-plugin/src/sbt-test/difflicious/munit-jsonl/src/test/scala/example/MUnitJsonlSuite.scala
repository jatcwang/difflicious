package example

import difflicious.Differ
import difflicious.munit.MUnitDiffliciousSuite
import munit.FunSuite

class MUnitJsonlSuite extends FunSuite with MUnitDiffliciousSuite {
  test("reports diff result") {
    Differ.useEquals[Int](_.toString).assertNoDiff(1, 2)
  }
}

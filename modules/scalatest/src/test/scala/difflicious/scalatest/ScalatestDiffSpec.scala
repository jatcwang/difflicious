package difflicious.scalatest

import difflicious.Differ
import munit.FunSuite

class ScalatestDiffSpec extends FunSuite {
  import ScalatestDiff._

  test("assertNoDiff succeeds when values are equal") {
    Differ.useEquals[Int](_.toString).assertNoDiff(1, 1)
  }

  test("assertNoDiff fails when values differ") {
    intercept[ScalatestDifferenceFoundException] {
      Differ.useEquals[Int](_.toString).assertNoDiff(1, 2)
    }
  }
}

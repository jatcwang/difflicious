package difflicious

import difflicious.DiffResultPrinter.consoleOutput
import difflicious.implicits._
import munit.Assertions.assertEquals
import org.scalacheck.Prop.{forAll, propBoolean}
import org.scalacheck.{Prop, Arbitrary}

package object testutils {

  val R = "\u001b[31m" // obtained (red)
  val G = "\u001b[32m" // expected (green)
  val I = "\u001b[90m" // ignore (dark grey)
  val X = "\u001b[39m" // terminal color reset
  val grayIgnoredStr = s"$I[IGNORED]$X"
  // Sometimes the [IGNORE] field exist in a obtained/expected-only object so it won't be colored
  val justIgnoredStr = s"[IGNORED]"

  def assertOkIfValuesEqualProp[A: Arbitrary](differ: Differ[A]): Prop = {
    forAll { l: A =>
      val res = differ.diff(l, l)
      res.isOk
    }
  }

  def assertNotOkIfNotEqualProp[A: Arbitrary](differ: Differ[A]): Prop = {
    forAll { (l: A, r: A) =>
      (l != r) ==> {
        val res = differ.diff(l, r)
        !res.isOk
      }
    }
  }

  def assertIsOkIfIgnoredProp[A: Arbitrary](differ: Differ[A]): Prop = {
    val differIgnored = differ.updateByStrPathOrFail(DifferOp.ignore)
    val differUnignored = differIgnored.updateByStrPathOrFail(DifferOp.unignored)
    forAll { (l: A, r: A) =>
      val ignoredResult = differIgnored.diff(l, r)
      assert(ignoredResult.isOk)
      assertDiffResultRender(
        ignoredResult,
        expectedOutputStr = grayIgnoredStr,
      )
      if (l == r) differUnignored.diff(l, r).isOk
      else !differUnignored.diff(l, r).isOk
    }
  }

  def assertDiffResultRender(
    res: DiffResult,
    expectedOutputStr: String,
  ): Unit = {
    val obtainedOutputStr = consoleOutput(res, 0).render

    if (obtainedOutputStr != expectedOutputStr) {
      println("=== Obtained Output === ")
      println(obtainedOutputStr)
      println("=== Expected Output === ")
      println(expectedOutputStr)
      assertEquals(obtainedOutputStr, expectedOutputStr)
    } else ()
  }

  def assertConsoleDiffOutput[A](
    differ: Differ[A],
    obtained: A,
    expected: A,
    expectedOutputStr: String,
  ): Unit = {
    val res = differ.diff(obtained, expected)
    assertDiffResultRender(res, expectedOutputStr)
  }

}

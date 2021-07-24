package difflicious

import difflicious.DiffResultPrinter.consoleOutput
import munit.Assertions.assertEquals
import org.scalacheck.Prop.{forAll, propBoolean}
import org.scalacheck.{Arbitrary, Prop}
import difflicious.internal.EitherGetSyntax._

package object testutils {

  val R = "\u001b[31m" // obtained (red)
  val G = "\u001b[32m" // expected (green)
  val I = "\u001b[90m" // ignore (dark grey)
  val X = "\u001b[39m" // terminal color reset
  val grayIgnoredStr = s"$I[IGNORED]$X"
  // Sometimes the [IGNORE] field exist in a obtained/expected-only object so it won't be colored
  val justIgnoredStr = s"[IGNORED]"

  def assertOkIfValuesEqualProp[A: Arbitrary](differ: Differ[A]): Prop = {
    forAll { (l: A) =>
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
    val differIgnored = differ.configureRaw(ConfigurePath.current, ConfigureOp.ignore).unsafeGet
    val differUnignored = differIgnored.configureRaw(ConfigurePath.current, ConfigureOp.unignore).unsafeGet
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

    // Reverse "recolor each line" difflicious.DiffResultPrinter.colorOnMatchType
    // to make test expectations easier to read and write
    def removeMultilineRecoloring(str: String): String = {
      val k = s"$X\n $R"
      val j = s"$X\n $G"
      val replaced = str.replace(k, "\n ").replace(j, "\n ")
      replaced
    }

    val obtainedOutputStr = removeMultilineRecoloring(consoleOutput(res, 0).render)

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

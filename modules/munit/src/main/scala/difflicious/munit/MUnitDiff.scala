package difflicious.munit

import difflicious.{Differ, DiffResultPrinter}
import munit.Assertions._
import munit.Location

trait MUnitDiff {
  implicit class DifferExtensions[A](differ: Differ[A]) {
    def assertNoDiff(obtained: A, expected: A)(implicit loc: Location): Unit = {
      val result = differ.diff(obtained, expected)
      if (!result.isOk)
        fail(DiffResultPrinter.consoleOutput(result, 0).render)(loc)
    }
  }
}

object MUnitDiff extends MUnitDiff

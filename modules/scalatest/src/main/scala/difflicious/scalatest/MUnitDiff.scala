package difflicious.scalatest

import difflicious.{Differ, DiffResultPrinter}
import org.scalactic.source.Position
import org.scalatest.Assertions.fail

trait MUnitDiff {
  implicit class DifferExtensions[A](differ: Differ[A]) {
    def assertNoDiff(obtained: A, expected: A)(implicit pos: Position): Unit = {
      val result = differ.diff(obtained, expected)
      if (!result.isOk)
        fail(DiffResultPrinter.consoleOutput(result, 0).render)(pos)
    }
  }
}

object MUnitDiff extends MUnitDiff

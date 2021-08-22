package difflicious.scalatest

import difflicious.{Differ, DiffResultPrinter}
import org.scalactic.source.Position
import org.scalatest.Assertions.fail

trait ScalatestDiff {
  extension [A](differ: Differ[A])
    inline def assertNoDiff(obtained: A, expected: A): Unit = {
      val result = differ.diff(obtained, expected)
      if (!result.isOk)
        fail(DiffResultPrinter.consoleOutput(result, 0).render)
    }
}

object ScalatestDiff extends ScalatestDiff

package difflicious.weaver

import difflicious.{Differ, DiffResultPrinter}
import weaver.Expectations
import weaver.Expectations.Helpers.{failure, success}

trait WeaverDiff {
  extension [A](differ: Differ[A])
    inline def assertNoDiff(obtained: A, expected: A): Expectations = {
      val result = differ.diff(obtained, expected)
      if (!result.isOk) failure(DiffResultPrinter.consoleOutput(result, 0).render)
      else success
    }
}

object WeaverDiff extends WeaverDiff

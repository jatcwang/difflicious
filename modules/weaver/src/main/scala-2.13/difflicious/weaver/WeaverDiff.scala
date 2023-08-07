package difflicious.weaver

import difflicious.{Differ, DiffResultPrinter}
import weaver.{Expectations, SourceLocation}
import weaver.Expectations.Helpers.{failure, success}

trait WeaverDiff {
  implicit class DifferExtensions[A](differ: Differ[A]) {
    def assertNoDiff(obtained: A, expected: A)(implicit pos: SourceLocation): Expectations = {
      val result = differ.diff(obtained, expected)
      if (!result.isOk) failure(DiffResultPrinter.consoleOutput(result, 0).render)
      else success
    }
  }
}

object WeaverDiff extends WeaverDiff

package difflicious.scalatest

import difflicious.Differ
import org.scalactic.source.Position

trait ScalatestDiff {
  implicit class DifferExtensions[A](differ: Differ[A]) {
    def assertNoDiff(obtained: A, expected: A)(implicit pos: Position): Unit = {
      differ.equalsOrDiff(obtained, expected).foreach { result =>
        if (!result.isOk)
          ScalatestDiffAssertions.failWithDiffResult(result)
      }
    }
  }
}

object ScalatestDiff extends ScalatestDiff

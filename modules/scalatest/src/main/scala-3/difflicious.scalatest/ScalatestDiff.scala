package difflicious.scalatest

import difflicious.Differ
import org.scalactic.source.Position

trait ScalatestDiff {
  extension [A](differ: Differ[A])
    inline def assertNoDiff(obtained: A, expected: A)(using pos: Position): Unit = {
      differ.equalsOrDiff(obtained, expected).foreach { result =>
        if (!result.isOk)
          ScalatestDiffAssertions.failWithDiffResult(result)
      }
    }
}

object ScalatestDiff extends ScalatestDiff

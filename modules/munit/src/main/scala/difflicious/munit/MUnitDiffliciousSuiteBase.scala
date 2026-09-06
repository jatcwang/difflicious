package difflicious.munit

import difflicious.Differ
import difflicious.reporter.DifferenceFoundException
import munit.{Location, Suite}

private[munit] trait MUnitDiffliciousSuiteBase extends Suite {
  implicit class DifferExtensions[A](differ: Differ[A]) {
    def assertNoDiff(obtained: A, expected: A)(implicit loc: Location): Unit = {
      differ.equalsOrDiff(obtained, expected).foreach { result =>
        if (!result.isOk)
          throw DifferenceFoundException(
            diffResult = result,
            fileName = loc.filename,
            filePath = loc.path,
            lineNumber = loc.line,
          )
      }
    }
  }
}

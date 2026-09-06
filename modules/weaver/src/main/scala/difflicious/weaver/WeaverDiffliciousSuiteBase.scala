package difflicious.weaver

import difflicious.Differ
import difflicious.reporter.DifferenceFoundException
import weaver.Expectations.Helpers.success
import weaver.{Expectations, FSuite}

private[weaver] trait WeaverDiffliciousSuiteBase[F[_]] extends FSuite[F] {
  implicit class DifferExtensions[A](differ: Differ[A]) {
    def assertNoDiff(obtained: A, expected: A): Expectations = {
      differ.equalsOrDiff(obtained, expected) match {
        case Some(result) if !result.isOk =>
          throw DifferenceFoundException(result, "", "", 0)
        case _ => success
      }
    }
  }
}

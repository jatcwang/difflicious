package difflicious.weaver

import cats.effect.IO
import difflicious.Differ
import weaver.SimpleIOSuite

object WeaverDiffliciousSuiteSpec extends SimpleIOSuite with WeaverDiffliciousSuite[IO] {
  pureTest("assertNoDiff succeeds when values are equal") {
    Differ.useEquals[Int](_.toString).assertNoDiff(1, 1)
  }
}

package example

import cats.effect.IO
import difflicious.Differ
import difflicious.weaver.WeaverDiffliciousSuite
import weaver.SimpleIOSuite

object WeaverJsonlSuite extends SimpleIOSuite with WeaverDiffliciousSuite[IO] {
  pureTest("reports diff result") {
    Differ.useEquals[Int](_.toString).assertNoDiff(1, 2)
  }

  test("reports effectful diff result") {
    IO.cede.flatMap(_ => IO(Differ.useEquals[Int](_.toString).assertNoDiff(2, 3)))
  }
}

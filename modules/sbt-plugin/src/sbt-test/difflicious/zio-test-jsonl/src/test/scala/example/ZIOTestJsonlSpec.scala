package example

import difflicious.Differ
import difflicious.ziotest.ZIODiffliciousSpec
import zio.{Scope, ZIO}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault}

object ZIOTestJsonlSpec extends ZIOSpecDefault with ZIODiffliciousSpec {
  private val ints = Differ.useEquals[Int](_.toString)

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ZIOTestJsonlSpec")(
      suite("inner")(
        test("reports diff result") {
          ints.assertNoDiff(1, 2)
        },
        test("reports effectful diff result") {
          ZIO.yieldNow.as(ints.assertNoDiff(2, 3))
        },
      ),
    )
}

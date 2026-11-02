package example

import difflicious.Differ
import difflicious.ziotest.ZIODiffliciousSpec
import zio.{Chunk, durationInt, Scope}
import zio.test.{Spec, TestAspect, TestAspectAtLeastR, TestEnvironment, ZIOSpecDefault}

object ZIOTestReplacedAspectsSpec extends ZIOSpecDefault with ZIODiffliciousSpec {
  private val ints = Differ.useEquals[Int](_.toString)

  override def aspects: Chunk[TestAspectAtLeastR[Environment & TestEnvironment]] =
    Chunk(TestAspect.timeout(60.seconds), diffliciousReporting)

  def spec: Spec[TestEnvironment & Scope, Any] =
    test("reports with replaced aspects") {
      ints.assertNoDiff(1, 2)
    }
}

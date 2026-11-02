package difflicious.ziotest

import difflicious.Differ
import difflicious.ziotest.ZIOInstancesSharedDiffers.linesBySku
import zio.{NonEmptyChunk, Scope}
import zio.test.{assertTrue, Spec, TestEnvironment, ZIOSpecDefault}

// Imports a configured differ at the top of the file, as a spec using shared differs would
object ZIOInstancesConfiguredDifferSpec extends ZIOSpecDefault with ZIODiffliciousSpec {
  private val lines = NonEmptyChunk(ZIOInstancesLine("apple", 1), ZIOInstancesLine("pear", 2))
  private val reordered = NonEmptyChunk(ZIOInstancesLine("pear", 2), ZIOInstancesLine("apple", 1))

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("a configured differ for NonEmptyChunk")(
      test("is used instead of the one a spec gets from the mixin") {
        val result = implicitly[Differ[NonEmptyChunk[ZIOInstancesLine]]].diff(lines, reordered)
        assertTrue(result.isOk)
      },
      test("is used when deriving a differ") {
        val differ = Differ.derived[ZIOInstancesOrder]
        val result = differ.diff(ZIOInstancesOrder(1, lines), ZIOInstancesOrder(1, reordered))
        assertTrue(result.isOk)
      },
    )
}

package difflicious.munit

import difflicious.utils.TypeName
import difflicious.{ConfigureError, ConfigureOp, ConfigurePath, Differ, DiffInput, DiffResult}
import munit.FunSuite

class MUnitDiffliciousSuiteSpec extends FunSuite with MUnitDiffliciousSuite {
  private val intTypeName = TypeName[Int]

  test("assertNoDiff skips diff when canUseEquals is true and values are equal") {
    var diffCalled = false
    val differ = trackingDiffer(canUseEqualsValue = true) {
      diffCalled = true
      fail("diff should not be called")
    }

    differ.assertNoDiff(1, 1)

    assertEquals(diffCalled, false)
  }

  test("assertNoDiff still diffs equal values when canUseEquals is false") {
    var diffCalled = false
    val differ = trackingDiffer(canUseEqualsValue = false) {
      diffCalled = true
      DiffResult.ValueResult.Both(intTypeName, "1", "1", isSame = true, isIgnored = false)
    }

    differ.assertNoDiff(1, 1)

    assertEquals(diffCalled, true)
  }

  private def trackingDiffer(canUseEqualsValue: Boolean)(onDiff: => DiffResult): Differ[Int] =
    new Differ[Int] {
      override type R = DiffResult

      override val canUseEquals: Boolean = canUseEqualsValue

      override def diff(inputs: DiffInput[Int]): DiffResult = onDiff

      override protected def configureIgnored(newIgnored: Boolean): Differ[Int] = this

      override protected def configurePath(
        step: String,
        nextPath: ConfigurePath,
        op: ConfigureOp,
      ): Either[ConfigureError, Differ[Int]] = Left(ConfigureError.PathTooLong(nextPath))

      override protected def configurePairBy(
        path: ConfigurePath,
        op: ConfigureOp.PairBy[?],
      ): Either[ConfigureError, Differ[Int]] = Left(ConfigureError.InvalidConfigureOp(path, op, "TrackingDiffer"))
    }
}

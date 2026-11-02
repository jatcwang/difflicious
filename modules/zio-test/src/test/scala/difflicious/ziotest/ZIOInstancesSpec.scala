package difflicious.ziotest

import difflicious.{Differ, DiffResult, DiffResultPrinter}
import difflicious.implicits.*
import zio.{NonEmptyChunk, Scope}
import zio.test.{assertTrue, Spec, TestEnvironment, ZIOSpecDefault}

final case class ZIOInstancesLine(sku: String, qty: Int)

object ZIOInstancesLine {
  implicit val differ: Differ[ZIOInstancesLine] = Differ.derived[ZIOInstancesLine]
}

final case class ZIOInstancesOrder(id: Int, lines: NonEmptyChunk[ZIOInstancesLine])

// Outside a spec, so the only instance in scope is the imported one
private[ziotest] object ZIOInstancesSharedDiffers {
  import difflicious.ziotest.implicits.*

  val ints: Differ[NonEmptyChunk[Int]] = Differ[NonEmptyChunk[Int]]

  implicit val linesBySku: Differ[NonEmptyChunk[ZIOInstancesLine]] =
    Differ.seqDiffer[NonEmptyChunk, ZIOInstancesLine].pairBy(_.sku)
}

// Builds a differ while the object is initialising, before ZIOInstances has initialised
private[ziotest] trait ZIOInstancesEarlyDiffer { self: ZIOInstances =>
  val ints: Differ[NonEmptyChunk[Int]] = Differ[NonEmptyChunk[Int]]
}

private[ziotest] object ZIOInstancesEarlyDifferUser extends ZIOInstancesEarlyDiffer with ZIOInstances

object ZIOInstancesSpec extends ZIOSpecDefault with ZIODiffliciousSpec {
  private val apple = ZIOInstancesLine("apple", 1)
  private val pear = ZIOInstancesLine("pear", 2)

  private def rendered(result: DiffResult): String = DiffResultPrinter.consoleOutput(result, 0).plainText

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("NonEmptyChunk")(
      test("has a differ in a spec without an import") {
        val differ = Differ[NonEmptyChunk[Int]]
        val result = differ.diff(NonEmptyChunk(1, 2), NonEmptyChunk(1, 3))
        val failed = differ.assertNoDiff(NonEmptyChunk(1, 2), NonEmptyChunk(1, 3))
        val passed = differ.assertNoDiff(NonEmptyChunk(1, 2), NonEmptyChunk(1, 2))
        assertTrue(rendered(result) == "NonEmptyChunk(\n  1,\n  2 -> 3\n)", failed.isFailure, passed.isSuccess)
      },
      test("has a differ elsewhere through an import") {
        val result = ZIOInstancesSharedDiffers.ints.diff(NonEmptyChunk(1, 2), NonEmptyChunk(1, 3))
        assertTrue(rendered(result) == "NonEmptyChunk(\n  1,\n  2 -> 3\n)")
      },
      test("is used when deriving a differ") {
        val differ = Differ.derived[ZIOInstancesOrder]
        val result = differ.diff(
          ZIOInstancesOrder(1, NonEmptyChunk(apple, pear)),
          ZIOInstancesOrder(1, NonEmptyChunk(apple, pear.copy(qty = 3))),
        )
        assertTrue(!result.isOk, rendered(result).contains("qty: 2 -> 3"))
      },
      test("can pair elements by a key") {
        val differ = Differ[NonEmptyChunk[ZIOInstancesLine]].pairBy(_.sku)
        val reordered = differ.diff(NonEmptyChunk(apple, pear), NonEmptyChunk(pear, apple))
        val changed = differ.diff(NonEmptyChunk(apple, pear), NonEmptyChunk(pear.copy(qty = 3), apple))
        assertTrue(reordered.isOk, !changed.isOk, rendered(changed).contains("qty: 2 -> 3"))
      },
      test("can ignore a field of every element") {
        val differ = Differ[NonEmptyChunk[ZIOInstancesLine]].ignoreAt(_.each.qty)
        val result = differ.diff(NonEmptyChunk(apple), NonEmptyChunk(apple.copy(qty = 3)))
        assertTrue(result.isOk)
      },
      test("has a working differ in a trait mixed in before ZIOInstances") {
        val result = ZIOInstancesEarlyDifferUser.ints.diff(NonEmptyChunk(1), NonEmptyChunk(2))
        assertTrue(!result.isOk)
      },
    )
}

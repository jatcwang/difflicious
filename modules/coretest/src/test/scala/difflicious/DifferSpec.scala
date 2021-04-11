package difflicious

import difflicious.DiffResultPrinter.{consoleOutput, consolePrint}
import munit.FunSuite
import difflicious.utils._
import difflicious.implicits._

// FIXME:
class DifferSpec extends FunSuite {

  private val R = "\u001b[31m" // actual (red)
  private val G = "\u001b[32m" // expected (green)
  private val I = "\u001b[90m" // ignore (dark grey)
  private val X = "\u001b[39m" // terminal color reset

  test("test fail") {
    consolePrint(
      checkDiff(CC(1, "asdf", 2.0), CC(1, "asdf", 3.0))(
        CC.differ
          .updateWith(
            UpdatePath.of(UpdateStep.DownPath("i")),
            DifferOp.SetIgnored(true),
          )
          .unsafeGet,
      ),
    )
  }

  test("adsf") {
    consolePrint(
      checkDiff[Map[Int, Foo]](Map(1 -> Sub1(1)), Map(1 -> SubSub2(2))),
    )
  }

  test("asff") {
    implicit val setD: Differ.SetDiffer[Set, CC] = Differ.setDiffer[Set, CC].matchBy(_.i)
    consolePrint(
      checkDiff(
        Set(
          CC(1, "s1", 1),
          CC(2, "s2", 2),
          CC(3, "s2", 2),
        ),
        Set(
          CC(1, "s2", 1),
          CC(2, "s1", 2),
          CC(4, "s2", 2),
        ),
      ),
    )
  }

  test("diff") {
    println(consoleOutput(checkDiff(1, 2), indentLevel = 0))
  }

  test("seq") {
    assertConsoleDiffOutput(
      Differ
        .seqDiffer[List, CC]
        .updateByStrPathUnsafe(DifferOp.ignore, "each", "dd"),
      List(
        CC(1, "s1", 1),
        CC(2, "s2", 2),
        CC(3, "s2", 2),
      ),
      List(
        CC(1, "s2", 1),
        CC(2, "s1", 2),
        CC(4, "s2", 3),
      ),
      s"""List(
         |  CC(
         |    i: 1,
         |    s: $R"s1"$X -> $G"s2"$X,
         |    dd: $I[IGNORED]$X,
         |  ),
         |  CC(
         |    i: 2,
         |    s: $R"s2"$X -> $G"s1"$X,
         |    dd: $I[IGNORED]$X,
         |  ),
         |  CC(
         |    i: ${R}3$X -> ${G}4$X,
         |    s: "s2",
         |    dd: $I[IGNORED]$X,
         |  ),
         |)""".stripMargin,
    )
  }

  private def assertConsoleDiffOutput[A](
    differ: Differ[A],
    a: A,
    b: A,
    expectedOutputStr: String,
  ): Unit = {
    val res = differ.diff(a, b)
    val actualOutputStr = consoleOutput(res, 0).render

    if (actualOutputStr != expectedOutputStr) {
      println("=== Actual Output === ")
      println(actualOutputStr)
      println("=== Expected Output === ")
      println(expectedOutputStr)
      assertEquals(actualOutputStr, expectedOutputStr)
    } else ()
  }
}

case class CC(i: Int, s: String, dd: Double)

object CC {
  implicit val differ: Differ[CC] = DiffGen.derive[CC]
}

sealed trait Foo

object Foo {
  implicit val differ: Differ[Foo] = DiffGen.derive[Foo]
}

case class Sub1(i: Int) extends Foo

sealed trait Foo2 extends Foo
case class SubSub1(d: Double) extends Foo2
case class SubSub2(i: Int) extends Foo2

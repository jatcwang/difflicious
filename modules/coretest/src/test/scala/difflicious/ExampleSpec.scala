package difflicious

import munit.FunSuite
import difflicious.testutils._

// FIXME:
class ExampleSpec extends FunSuite {

  test("test fail") {
    pprint.pprintln(
      checkDiff(Blah(1, "asdf", 2.0), Blah(1, "asdf", 3.0))(
        Blah.differ
          .updateWith(
            UpdatePath.of(UpdateStep.RecordField("i")),
            DifferOp.SetIgnored(true),
          )
          .unsafeGet,
      ),
    )
  }

  test("adsf") {
    pprint.pprintln(
      checkDiff[Map[Int, Foo]](Map(1 -> X(1)), Map(1 -> X(2))),
    )
  }

  test("asff") {
    implicit val setD: Differ.SetDiffer[Set, Blah] = Differ.setDiffer[Set, Blah].matchBy(_.i)
    pprint.pprintln(
      checkDiff(
        Set(
          Blah(1, "s1", 1),
          Blah(2, "s2", 2),
          Blah(3, "s2", 2),
        ),
        Set(
          Blah(1, "s2", 1),
          Blah(2, "s1", 2),
          Blah(4, "s2", 2),
        ),
      ),
    )
  }
}

case class Blah(i: Int, s: String, kkk: Double)

object Blah {
  implicit val differ: Differ[Blah] = DiffGen.derive[Blah]
}

sealed trait Foo

object Foo {
  implicit val differ: Differ[Foo] = DiffGen.derive[Foo]
}

case class X(i: Int) extends Foo

sealed trait Foo2 extends Foo
case class F1(d: Double) extends Foo2
case class F2(i: Int) extends Foo2

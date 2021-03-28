package difflicious

import munit.FunSuite
import difflicious.testutils._

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
}

case class Blah(i: Int, s: String, kkk: Double)

object Blah {
  implicit val differ: Differ[Blah] = DiffGen.derive[Blah]
}

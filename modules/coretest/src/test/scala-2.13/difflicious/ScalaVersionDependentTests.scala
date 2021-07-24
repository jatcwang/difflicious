package difflicious

import difflicious.testtypes._
import difflicious.testutils._
import munit.FunSuite

trait ScalaVersionDependentTests { this: FunSuite =>
  test("configure path's subType handles multi-level hierarchies") {
    assertConsoleDiffOutput(
      Differ[List[Sealed]].ignoreAt(_.each.subType[SubSealed.SubSub1].d),
      List(
        SubSealed.SubSub1(1.0),
      ),
      List(
        SubSealed.SubSub1(2.0),
      ),
      s"""List(
         |  SubSub1(
         |    d: $grayIgnoredStr,
         |  ),
         |)""".stripMargin,
    )
  }

}

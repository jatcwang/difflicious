package difflicious

import difflicious.testtypes._
import difflicious.testtypes.SealedNested.SubSealed
import difflicious.testutils._
import difflicious.implicits._
import munit.FunSuite

trait ScalaVersionDependentTests { this: FunSuite =>
  test("configure path's subType handles multi-level hierarchies") {
    assertConsoleDiffOutput(
      Differ[List[SealedNested]].ignoreAt(_.each.subType[SubSealed.SubSub1].d),
      List(
        SubSealed.SubSub1(1.0),
      ),
      List(
        SubSealed.SubSub1(2.0),
      ),
      s"""List(
         |  SubSub1(
         |    d: $grayIgnoredStr
         |  )
         |)""".stripMargin,
    )
  }

  test("configure path's subType call errors when super type isn't sealed") {
    val compileError = compileErrors("Differ[List[OpenSuperType]].ignoreAt(_.each.subType[OpenSub])")
    val firstLine = compileError.linesIterator.toList.drop(1).head
    assertEquals(firstLine, "Specified subtype is not a known direct subtype of trait OpenSuperType.")
  }

}

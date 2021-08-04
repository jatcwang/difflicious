package difflicious

import difflicious.testtypes._
import difflicious.implicits._

trait ScalaVersionDependentTests { this: munit.FunSuite =>

  test("configure path's subType call errors when super type isn't sealed") {
    val compileError = compileErrors("Differ[List[OpenSuperType]].ignoreAt(_.each.subType[OpenSub])")
    val firstLine = compileError.linesIterator.toList.head
    println(compileError)
    assertEquals(
      firstLine,
      "error: subType requires that the super type be a sealed trait (enum), and the subtype being a direct children of the super type.",
    )
  }

}

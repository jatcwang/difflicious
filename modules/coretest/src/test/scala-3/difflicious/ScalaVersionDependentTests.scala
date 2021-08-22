package difflicious

import difflicious.testtypes.*
import difflicious.implicits.*
import difflicious.testutils.*

trait ScalaVersionDependentTests:
  this: munit.FunSuite =>

  test("configure path's subType call errors when super type isn't sealed") {
    val compileError = compileErrors("Differ[List[OpenSuperType]].ignoreAt(_.each.subType[OpenSub])")
    val firstLine = compileError.linesIterator.toList.head
    assertEquals(
      firstLine,
      "error: subType requires that the super type be a sealed trait (enum), and the subtype being a direct children of the super type.",
    )
  }

  test("Derived Enum: isOk == true if two values are equal") {
    assertOkIfValuesEqualProp(MyEnum.given_Differ_MyEnum)
  }

  test("Derived Enum: isOk == false if two values are NOT equal") {
    assertNotOkIfNotEqualProp(MyEnum.given_Differ_MyEnum)
  }

  test("Derived Enum: isOk always true if differ is marked ignored") {
    assertIsOkIfIgnoredProp(MyEnum.given_Differ_MyEnum)
  }

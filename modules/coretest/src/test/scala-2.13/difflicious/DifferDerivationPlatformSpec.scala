package difflicioustest

import difflicious.testtypes.*
import difflicious.Differ

// Note: When adding/modifying this file, make sure to edit the equivalent scala 3 test file too (if applicable)
class DifferDerivationPlatformSpec extends munit.FunSuite {

  test("Auto derivation finds a Differ for a product") {
    import difflicious.generic.auto.*

    val result = Differ[SimpleCaseClass1].diff(SimpleCaseClass1("a"), SimpleCaseClass1("b"))
    assertEquals(result.isOk, false)
  }

  test("Auto derivation materializes directly as a Differ") {
    val derived: Differ[SimpleCaseClass1] = {
      import difflicious.generic.auto.*

      Differ[SimpleCaseClass1]
    }

    val result = {
      implicit val d: Differ[SimpleCaseClass1] = derived

      Differ[SimpleCaseClass1].diff(SimpleCaseClass1("a"), SimpleCaseClass1("b"))
    }
    assertEquals(result.isOk, false)
  }

  test("Auto derivation uses manually defined instances for fields") {
    import difflicious.generic.auto.*

    implicit val d: Differ[SimpleCaseClass1] =
      Differ.derived[SimpleCaseClass1].ignoreAt(_.value)

    val result = Differ[SimpleCaseClassSubject].diff(
      SimpleCaseClassSubject(SimpleCaseClass1("a"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
      SimpleCaseClassSubject(SimpleCaseClass1("b"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
    )
    assertEquals(result.isOk, true)
  }

  test("Auto derivation recursively derives missing case class field instances") {
    import difflicious.generic.auto.*

    val subject: Differ[SimpleCaseClassSubject] = Differ[SimpleCaseClassSubject]
    val result = subject.diff(
      SimpleCaseClassSubject(SimpleCaseClass1("a"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
      SimpleCaseClassSubject(SimpleCaseClass1("b"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
    )
    assertEquals(result.isOk, false)
  }

}

package difflicioustest

import difflicious.testtypes.*
import difflicious.Differ
import difflicious.testutils.assertStartsWith

// Note: When adding/modifying this file, make sure to edit the equivalent scala 2 test file too (if applicable)
class DifferDerivationPlatformSpec extends munit.FunSuite:

  test("Auto derivation finds a Differ for a product") {
    import difflicious.generic.auto.given

    val result = Differ[SimpleCaseClass1].diff(SimpleCaseClass1("a"), SimpleCaseClass1("b"))
    assertEquals(result.isOk, false)
  }

  test("Auto derivation materializes directly as a Differ") {
    val derived: Differ[SimpleCaseClass1] = {
      import difflicious.generic.auto.given

      Differ[SimpleCaseClass1]
    }

    val result = {
      given Differ[SimpleCaseClass1] = derived

      Differ[SimpleCaseClass1].diff(SimpleCaseClass1("a"), SimpleCaseClass1("b"))
    }
    assertEquals(result.isOk, false)
  }

  test("Auto derivation uses manually defined instances for fields") {
    import difflicious.generic.auto.given

    given Differ[SimpleCaseClass1] =
      Differ.derived[SimpleCaseClass1].ignoreAt(_.value)

    val result = Differ[SimpleCaseClassSubject].diff(
      SimpleCaseClassSubject(SimpleCaseClass1("a"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
      SimpleCaseClassSubject(SimpleCaseClass1("b"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
    )
    assertEquals(result.isOk, true)
  }

  test("Auto derivation recursively derives missing case class field instances") {
    import difflicious.generic.auto.given

    val subject: Differ[SimpleCaseClassSubject] = Differ[SimpleCaseClassSubject]
    val result = subject.diff(
      SimpleCaseClassSubject(SimpleCaseClass1("a"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
      SimpleCaseClassSubject(SimpleCaseClass1("b"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
    )
    assertEquals(result.isOk, false)
  }

  test("Fully automatic derivation reports the failure tree when recursive derivation fails") {
    val result = compileErrors("""
        import difflicious.*
        import difflicious.generic.auto.given
        import difflicious.testtypes.*

        Differ[DerivationFailureSubject]
        """)

    assertStartsWith(
      result,
      """error:
        |Failed to derive Differ[DerivationFailureSubject]
        |
        |DerivationFailureSubject
        |  Differ[SomeTrait] cannot be found or derived
        |  Differ[DerivationFailureNested] cannot be derived because...
        |    Differ[SomeTrait] cannot be found or derived
        |    Differ[SomeOtherTrait] cannot be found or derived
        |
        |Summary: Derivation failed because we couldn't derive Differ[SomeTrait], Differ[SomeOtherTrait]""".stripMargin,
    )
  }

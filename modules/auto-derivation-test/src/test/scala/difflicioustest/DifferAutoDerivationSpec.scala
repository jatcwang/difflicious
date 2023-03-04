package difflicioustest

import munit.ScalaCheckSuite

class DifferSpec extends ScalaCheckSuite {
  test("should not compile without instance in scope") {
    val result = compileErrors("""
        import difflicious._
        final case class P1(f1: String)
        
        val p1: Differ[P1] = Differ.derived[P1]

        Differ[P1].diff(P1("a"), P1("b"))
        """)
    assertNoDiff(
      result,
      """error:
implicit error;
!I differ: difflicious.Differ[P1]
        Differ[P1].diff(P1("a"), P1("b"))
              ^""",
    )
  }
  test("should find auto derived instance for product") {
    val result = compileErrors("""
        import difflicious._
        import difflicious.generic.auto._

        final case class P1(f1: String)
        
        Differ[P1].diff(P1("a"), P1("b"))
        """)
    assertNoDiff(result, "")
  }
}

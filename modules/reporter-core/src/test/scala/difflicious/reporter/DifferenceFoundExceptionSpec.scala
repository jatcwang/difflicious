package difflicious.reporter

import difflicious.DiffResult.ValueResult
import munit.FunSuite

class DifferenceFoundExceptionSpec extends FunSuite {
  test("difference exception message contains its test id") {
    val result =
      ValueResult.Both(difflicious.utils.TypeName[Int], "obtained", "expected", isSame = false, isIgnored = false)
    val exception = DifferenceFoundException(
      diffResult = result,
      fileName = "ExampleSuite.scala",
      filePath = "/workspace/ExampleSuite.scala",
      lineNumber = 37,
    )

    assert(Ulid.isValid(exception.testId))
    assert(exception.getMessage.startsWith(s"Test id: ${exception.testId}\n"))
    assert(exception.getMessage.contains("obtained"))
    assert(exception.getMessage.contains("expected"))
  }
}

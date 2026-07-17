package difflicious.cli

import munit.FunSuite
import snapshot4s.generated.*
import snapshot4s.munit.SnapshotAssertions

class CliParserSpec extends FunSuite with SnapshotAssertions {
  test("parses JSONL report inputs with json mode") {
    assertEquals(
      CliParser.parse(List("--json", "target/difflicious-result")),
      Right(
        CliCommand.Run(
          CliConfig(
            mode = RunMode.NonInteractive(OutputFormat.Json),
            input = CliInput.Report(Vector("target/difflicious-result")),
            testId = None,
            color = true,
          ),
        ),
      ),
    )
  }

  test("parses multiple JSONL report inputs with interactive mode and no color") {
    assertEquals(
      CliParser.parse(List("--interactive", "--no-color", "suite-a.jsonl", "suite-b.jsonl")),
      Right(
        CliCommand.Run(
          CliConfig(
            mode = RunMode.Tui,
            input = CliInput.Report(Vector("suite-a.jsonl", "suite-b.jsonl")),
            testId = None,
            color = false,
          ),
        ),
      ),
    )
  }

  test("format option is not supported") {
    val error = CliParser.parse(List("--format", "json", "failures.jsonl")).left.toOption.getOrElse("")

    assert(error.contains("--format"))
  }

  test("raw JSON comparison options are not supported") {
    val error =
      CliParser.parse(List("--obtained", "left.json", "--expected", "right.json")).left.toOption.getOrElse("")

    assert(error.contains("--obtained"))
  }

  test("parses test id filter for JSONL report inputs") {
    assertEquals(
      CliParser.parse(List("--test-id", "01ARZ3NDEKTSV4RRFFQ69G5FAW", "--plain", "failures.jsonl")),
      Right(
        CliCommand.Run(
          CliConfig(
            mode = RunMode.NonInteractive(OutputFormat.Plain),
            input = CliInput.Report(Vector("failures.jsonl")),
            testId = Some("01ARZ3NDEKTSV4RRFFQ69G5FAW"),
            color = true,
          ),
        ),
      ),
    )
  }

  test("defaults JSONL report input to target difflicious result directory") {
    assertEquals(
      CliParser.parse(Nil),
      Right(
        CliCommand.Run(
          CliConfig(
            mode = RunMode.default,
            input = CliInput.Report(Vector("target/difflicious-result")),
            testId = None,
            color = true,
          ),
        ),
      ),
    )
  }

  test("reports conflicting mode selectors") {
    val error = CliParser.parse(List("--json", "--plain", "failures.jsonl")).left.toOption.getOrElse("")

    assert(error.contains("Choose only one output mode"))
  }

  test("help lists only the supported output mode flags") {
    assertFileSnapshot(CliParser.usage + "\n", "CliParserSpec/help.snap")
  }

  test("parses help as a help command") {
    assertEquals(CliParser.parse(List("--help")), Right(CliCommand.ShowHelp))
    assertEquals(CliParser.parse(List("-h")), Right(CliCommand.ShowHelp))
  }
}

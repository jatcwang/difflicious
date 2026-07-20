package difflicious.cli

import difflicious.DiffResult.ValueResult
import difflicious.reporter.DiffResultJson
import io.circe.Json
import io.circe.parser.parse
import munit.FunSuite
import snapshot4s.generated.*
import snapshot4s.munit.SnapshotAssertions

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import MainSpec._

class MainSpec extends FunSuite with SnapshotAssertions {
  private val ExampleRunId = "01ARZ3NDEKTSV4RRFFQ69G5FAV"
  private val ExampleTestId = "01ARZ3NDEKTSV4RRFFQ69G5FAW"
  private val OtherTestId = "01ARZ3NDEKTSV4RRFFQ69G5FAX"

  test("plain mode reads ScalaTest listener JSONL report") {
    val report = tempJsonl(
      listenerJsonl("ExampleSuite", "example.ExampleSuite", "/workspace/ExampleSuite.scala", 37),
    )
    val output = new ByteArrayOutputStream
    val error = new ByteArrayOutputStream

    val exitCode = Main.run(
      List("--plain", "-d", report.toString),
      emptyStdin,
      printStream(output),
      printStream(error),
    )

    val rendered = output.toString(StandardCharsets.UTF_8.name())
    assertEquals(exitCode, 0)
    assertEquals(error.toString(StandardCharsets.UTF_8.name()), "")
    assertFileSnapshot(rendered, "MainSpec/plain-listener-report.snap")
  }

  test("json mode reads all JSONL reports from a directory") {
    val directory = Files.createTempDirectory("difflicious-cli-jsonl-dir")
    val runDirectory = directory.resolve(ExampleRunId)
    Files.createDirectories(runDirectory)
    Files.writeString(
      runDirectory.resolve("example.ExampleSuite.jsonl"),
      listenerJsonl("ExampleSuite", "example.ExampleSuite", "/workspace/ExampleSuite.scala", 37),
    )
    Files.writeString(
      runDirectory.resolve("example.OtherSuite.jsonl"),
      listenerJsonl("OtherSuite", "example.OtherSuite", "/workspace/OtherSuite.scala", 58),
    )
    Files.writeString(directory.resolve("ignored.txt"), "not jsonl")
    val output = new ByteArrayOutputStream
    val error = new ByteArrayOutputStream

    val exitCode = Main.run(
      List("--json", "-d", directory.toString),
      emptyStdin,
      printStream(output),
      printStream(error),
    )

    val json = parse(output.toString(StandardCharsets.UTF_8.name())).toOption.get
    assertEquals(exitCode, 0)
    assertEquals(error.toString(StandardCharsets.UTF_8.name()), "")
    assertEquals(json.hcursor.downField("summary").get[Int]("failures"), Right(2))
    assertEquals(json.hcursor.downField("summary").get[Int]("totalChanges"), Right(2))
  }

  test("test id filters non-interactive report output") {
    val directory = Files.createTempDirectory("difflicious-cli-test-id-filter")
    Files.writeString(
      directory.resolve("example.ExampleSuite.jsonl"),
      listenerJsonl("ExampleSuite", "example.ExampleSuite", "/workspace/ExampleSuite.scala", 37),
    )
    Files.writeString(
      directory.resolve("example.OtherSuite.jsonl"),
      listenerJsonl(
        suiteName = "OtherSuite",
        suiteId = "example.OtherSuite",
        filePath = "/workspace/OtherSuite.scala",
        lineNumber = 58,
        testName = "compares other values",
        testId = OtherTestId,
      ),
    )
    val output = new ByteArrayOutputStream
    val error = new ByteArrayOutputStream

    val exitCode = Main.run(
      List("--json", "--test-id", OtherTestId, "-d", directory.toString),
      emptyStdin,
      printStream(output),
      printStream(error),
    )

    val json = parse(output.toString(StandardCharsets.UTF_8.name())).toOption.get
    val firstFailure = json.hcursor.downField("failures").downArray
    assertEquals(exitCode, 0)
    assertEquals(error.toString(StandardCharsets.UTF_8.name()), "")
    assertEquals(json.hcursor.downField("summary").get[Int]("failures"), Right(1))
    assertEquals(
      firstFailure.downField("metadata").get[String]("runId"),
      Right(ExampleRunId),
    )
    assertEquals(
      firstFailure.downField("metadata").get[String]("testId"),
      Right(OtherTestId),
    )
    assertEquals(firstFailure.downField("metadata").get[String]("filePath"), Right("/workspace/OtherSuite.scala"))
  }

  test("test id starts TUI on the matching report failure") {
    val directory = Files.createTempDirectory("difflicious-cli-test-id-tui")
    Files.writeString(
      directory.resolve("example.ExampleSuite.jsonl"),
      listenerJsonl("ExampleSuite", "example.ExampleSuite", "/workspace/ExampleSuite.scala", 37),
    )
    Files.writeString(
      directory.resolve("example.OtherSuite.jsonl"),
      listenerJsonl(
        suiteName = "OtherSuite",
        suiteId = "example.OtherSuite",
        filePath = "/workspace/OtherSuite.scala",
        lineNumber = 58,
        testName = "compares other values",
        testId = OtherTestId,
      ),
    )
    val output = new ByteArrayOutputStream
    val error = new ByteArrayOutputStream
    val tuiRunner = new RecordingTuiRunner

    val exitCode = Main.run(
      List("--interactive", "--no-color", "--test-id", OtherTestId, "-d", directory.toString),
      emptyStdin,
      printStream(output),
      printStream(error),
      tuiRunner,
    )

    assertEquals(exitCode, 0)
    assertEquals(output.toString(StandardCharsets.UTF_8.name()), "")
    assertEquals(error.toString(StandardCharsets.UTF_8.name()), "")
    assertEquals(tuiRunner.calls.size, 1)
    assertEquals(tuiRunner.calls.head.initialIndex, 1)
    assertEquals(tuiRunner.calls.head.color, false)
    assertEquals(tuiRunner.calls.head.report.runs.size, 2)
    assertEquals(tuiRunner.calls.head.report.runs(1).metadata.map(_.runId), Some(ExampleRunId))
    assertEquals(tuiRunner.calls.head.report.runs(1).metadata.map(_.testId), Some(OtherTestId))
  }

  test("test id reports an error when no report failure matches") {
    val report = tempJsonl(listenerJsonl("ExampleSuite", "example.ExampleSuite", "/workspace/ExampleSuite.scala", 37))
    val output = new ByteArrayOutputStream
    val error = new ByteArrayOutputStream

    val exitCode = Main.run(
      List("--plain", "--test-id", "missing-test", "-d", report.toString),
      emptyStdin,
      printStream(output),
      printStream(error),
    )

    assertEquals(exitCode, 2)
    assertEquals(output.toString(StandardCharsets.UTF_8.name()), "")
    assert(error.toString(StandardCharsets.UTF_8.name()).contains("No diff failure matched test id 'missing-test'"))
  }

  private def tempJsonl(content: String): Path = {
    val file = Files.createTempFile("difflicious-cli-", ".jsonl")
    Files.writeString(file, content)
    file
  }

  private def listenerJsonl(
    suiteName: String,
    suiteId: String,
    filePath: String,
    lineNumber: Int,
    testName: String = "compares values",
    testHierarchy: Vector[String] = Vector.empty,
    runId: String = ExampleRunId,
    testId: String = ExampleTestId,
  ): String = {
    val testText = leafText(testName, testHierarchy)
    val hierarchy = if (testHierarchy.isEmpty) Vector(testName) else testHierarchy
    val diffResult = ValueResult.Both("1", "2", isSame = false, isIgnored = false)
    val fields = Vector(
      "runId" -> Json.fromString(runId),
      "testId" -> Json.fromString(testId),
      "suiteName" -> Json.fromString(suiteName),
      "suiteId" -> Json.fromString(suiteId),
      "suiteClassName" -> Json.fromString(suiteId),
      "testName" -> Json.fromString(testName),
      "testText" -> Json.fromString(testText),
      "testHierarchy" -> Json.fromValues(hierarchy.map(Json.fromString)),
      "fileName" -> Json.fromString("ExampleSuite.scala"),
      "filePath" -> Json.fromString(filePath),
      "lineNumber" -> Json.fromInt(lineNumber),
      "diffResult" -> parse(DiffResultJson.toJsonString(diffResult)).fold(error => throw error, identity),
    )
    val record = Json.fromFields(fields)

    record.noSpaces + System.lineSeparator()
  }

  private def leafText(testName: String, testHierarchy: Vector[String]): String =
    testHierarchy.lastOption.getOrElse(testName)

  private def emptyStdin: ByteArrayInputStream =
    new ByteArrayInputStream(Array.emptyByteArray)

  private def printStream(output: ByteArrayOutputStream): PrintStream =
    new PrintStream(output, true, StandardCharsets.UTF_8.name())

}

private object MainSpec {
  final class RecordingTuiRunner extends TuiRunner {
    var calls: Vector[TuiCall] = Vector.empty

    override def run(report: DiffReport, color: Boolean, initialIndex: Int): Unit =
      calls = calls :+ TuiCall(report, color, initialIndex)
  }

  final case class TuiCall(report: DiffReport, color: Boolean, initialIndex: Int)
}

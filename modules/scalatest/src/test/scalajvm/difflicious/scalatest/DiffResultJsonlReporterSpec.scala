package difflicious.scalatest

import difflicious.DiffResult.ValueResult
import difflicious.reporter.DiffliciousResultDefaults
import difflicious.reporter.UlidGenerator
import io.circe.Json
import io.circe.parser.parse
import munit.FunSuite
import org.scalactic.source.Position
import org.scalatest.Args
import org.scalatest.events.{NameInfo, Ordinal, ScopeOpened, TestFailed}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.funsuite.AnyFunSuite

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.jdk.CollectionConverters._

class DiffResultJsonlReporterSpec extends FunSuite {
  private val RunId = "01ARZ3NDEKTSV4RRFFQ69G5FAV"
  private val TestId = "01ARZ3NDEKTSV4RRFFQ69G5FAW"
  private val SecondTestId = "01ARZ3NDEKTSV4RRFFQ69G5FAX"
  private val ZeroRunDirectory = "19700101_000000"

  test("assertion failure carries diff result cause") {
    val result = ValueResult.Both("1", "2", isSame = false, isIgnored = false)
    implicit val pos: Position = Position("ExampleSuite.scala", "/workspace/ExampleSuite.scala", 37)

    val failure = intercept[TestFailedException] {
      ScalatestDiffAssertions.failWithDiffResult(result)
    }

    assertEquals(
      Option(failure.getCause).collect { case diffFailure: DiffTestFailure => diffFailure },
      Some(DiffTestFailure(result, "ExampleSuite.scala", "/workspace/ExampleSuite.scala", 37)),
    )
    assertEquals(failure.payload, None)
  }

  test("writes one JSONL file per suite for diff failures") {
    val outputDir = Files.createTempDirectory("difflicious-scalatest-jsonl")
    val reporter = zeroRunReporter(outputDir)
    val result = ValueResult.Both("1", "2", isSame = false, isIgnored = false)
    val diffFailure = DiffTestFailure(result, "ExampleSuite.scala", "/workspace/ExampleSuite.scala", 37)

    reporter(
      TestFailed(
        ordinal = new Ordinal(0),
        message = "diff failed",
        suiteName = "ExampleSuite",
        suiteId = "example.ExampleSuite",
        suiteClassName = Some("example.ExampleSuite"),
        testName = "compares values",
        testText = "compares values",
        recordedEvents = IndexedSeq.empty,
        analysis = IndexedSeq.empty,
        throwable = Some(testFailedException(diffFailure)),
        duration = Some(12L),
        formatter = None,
        location = None,
        rerunner = None,
        payload = None,
        threadName = "main",
        timeStamp = 123L,
      ),
    )

    val outputFile = outputDir.resolve(ZeroRunDirectory).resolve("example.ExampleSuite.jsonl")
    val lines = Files.readAllLines(outputFile, StandardCharsets.UTF_8)

    assertEquals(lines.size, 1)
    assertJsonl(
      lines,
      parseJson(
        """{
          |  "runId": "00000000000000000000000000",
          |  "testId": "01ARZ3NDEKTSV4RRFFQ69G5FAW",
          |  "suiteName": "ExampleSuite",
          |  "suiteId": "example.ExampleSuite",
          |  "suiteClassName": "example.ExampleSuite",
          |  "testName": "compares values",
          |  "testText": "compares values",
          |  "testHierarchy": [
          |    "compares values"
          |  ],
          |  "fileName": "ExampleSuite.scala",
          |  "filePath": "/workspace/ExampleSuite.scala",
          |  "lineNumber": 37,
          |  "durationMillis": 12,
          |  "timeStamp": 123,
          |  "diffResult": {
          |    "type": "value",
          |    "valueType": "both",
          |    "obtained": "1",
          |    "expected": "2",
          |    "isSame": false,
          |    "pairType": "both",
          |    "isIgnored": false,
          |    "isOk": false
          |  }
          |}""".stripMargin,
      ),
    )
  }

  test("writes configured run id") {
    val outputDir = Files.createTempDirectory("difflicious-scalatest-jsonl-test-run-id")
    val reporter = new DiffResultJsonlReporter(outputDir.toString, RunId, deterministicUlidGenerator())
    val result = ValueResult.Both("1", "2", isSame = false, isIgnored = false)
    val diffFailure = DiffTestFailure(result, "ExampleSuite.scala", "/workspace/ExampleSuite.scala", 37)

    reporter(
      TestFailed(
        ordinal = new Ordinal(0),
        message = "diff failed",
        suiteName = "ExampleSuite",
        suiteId = "example.ExampleSuite",
        suiteClassName = Some("example.ExampleSuite"),
        testName = "compares values",
        testText = "compares values",
        recordedEvents = IndexedSeq.empty,
        analysis = IndexedSeq.empty,
        throwable = Some(testFailedException(diffFailure)),
        duration = Some(12L),
        formatter = None,
        location = None,
        rerunner = None,
        payload = None,
        threadName = "main",
        timeStamp = 123L,
      ),
    )

    val outputFile = outputDir.resolve(RunId).resolve("example.ExampleSuite.jsonl")
    val line = Files.readAllLines(outputFile, StandardCharsets.UTF_8).get(0)

    assertEquals(jsonStringField(line, "runId"), RunId)
  }

  test("uses a timestamp directory when run id is zero") {
    val timestamp = Instant.parse("2026-07-15T10:15:10.987Z").toEpochMilli

    assertEquals(
      DiffResultJsonlReporter.directoryNameForRun(DiffliciousResultDefaults.ZeroUlid, timestamp),
      "20260715_101510",
    )
  }

  test("writes null JSON fields for absent optional ScalaTest metadata") {
    val outputDir = Files.createTempDirectory("difflicious-scalatest-jsonl-null-options")
    val reporter = zeroRunReporter(outputDir)
    val result = ValueResult.Both("1", "2", isSame = false, isIgnored = false)
    val diffFailure = DiffTestFailure(result, "ExampleSuite.scala", "/workspace/ExampleSuite.scala", 37)

    reporter(
      TestFailed(
        ordinal = new Ordinal(0),
        message = "diff failed",
        suiteName = "ExampleSuite",
        suiteId = "example/ExampleSuite",
        suiteClassName = None,
        testName = "compares values",
        testText = "compares values",
        recordedEvents = IndexedSeq.empty,
        analysis = IndexedSeq.empty,
        throwable = Some(testFailedException(diffFailure)),
        duration = None,
        formatter = None,
        location = None,
        rerunner = None,
        payload = None,
        threadName = "main",
        timeStamp = 123L,
      ),
    )

    val outputFile = outputDir.resolve(ZeroRunDirectory).resolve("example_ExampleSuite.jsonl")
    val lines = Files.readAllLines(outputFile, StandardCharsets.UTF_8)

    assertEquals(lines.size, 1)
    assertJsonl(
      lines,
      parseJson(
        """{
          |  "runId": "00000000000000000000000000",
          |  "testId": "01ARZ3NDEKTSV4RRFFQ69G5FAW",
          |  "suiteName": "ExampleSuite",
          |  "suiteId": "example/ExampleSuite",
          |  "suiteClassName": null,
          |  "testName": "compares values",
          |  "testText": "compares values",
          |  "testHierarchy": [
          |    "compares values"
          |  ],
          |  "fileName": "ExampleSuite.scala",
          |  "filePath": "/workspace/ExampleSuite.scala",
          |  "lineNumber": 37,
          |  "durationMillis": null,
          |  "timeStamp": 123,
          |  "diffResult": {
          |    "type": "value",
          |    "valueType": "both",
          |    "obtained": "1",
          |    "expected": "2",
          |    "isSame": false,
          |    "pairType": "both",
          |    "isIgnored": false,
          |    "isOk": false
          |  }
          |}""".stripMargin,
      ),
    )
  }

  test("captures diff assertion failures from ScalaTest events") {
    val outputDir = Files.createTempDirectory("difflicious-scalatest-run-jsonl")
    val suite = new DiffSuite
    val status = suite.run(None, Args(zeroRunReporter(outputDir)))

    status.waitUntilCompleted()
    assert(!status.succeeds())

    val outputFiles = listJsonlFiles(outputDir)
    assertEquals(outputFiles.size, 1)

    val lines = Files.readAllLines(outputFiles.head, StandardCharsets.UTF_8)
    assertEquals(lines.size, 1)
    assertJsonl(
      lines,
      parseJson(
        """{
          |  "runId": "00000000000000000000000000",
          |  "testId": "01ARZ3NDEKTSV4RRFFQ69G5FAW",
          |  "suiteName": "DiffResultJsonlReporterSpec$DiffSuite",
          |  "suiteId": "difflicious.scalatest.DiffResultJsonlReporterSpec$DiffSuite",
          |  "suiteClassName": "difflicious.scalatest.DiffResultJsonlReporterSpec$DiffSuite",
          |  "testName": "testDiffFailure",
          |  "testText": "testDiffFailure",
          |  "testHierarchy": [
          |    "testDiffFailure"
          |  ],
          |  "fileName": "DiffSuite.scala",
          |  "filePath": "/workspace/DiffSuite.scala",
          |  "lineNumber": 41,
          |  "durationMillis": 0,
          |  "timeStamp": 0,
          |  "diffResult": {
          |    "type": "value",
          |    "valueType": "both",
          |    "obtained": "obtained",
          |    "expected": "expected",
          |    "isSame": false,
          |    "pairType": "both",
          |    "isIgnored": false,
          |    "isOk": false
          |  }
          |}""".stripMargin,
      ),
    )
  }

  test("captures nested ScalaTest scopes in hierarchy") {
    val outputDir = Files.createTempDirectory("difflicious-scalatest-freespec-jsonl")
    val suite = new NestedDiffSuite
    val status = suite.run(None, Args(zeroRunReporter(outputDir)))

    status.waitUntilCompleted()
    assert(!status.succeeds())

    val outputFiles = listJsonlFiles(outputDir)
    assertEquals(outputFiles.size, 1)

    val lines = Files.readAllLines(outputFiles.head, StandardCharsets.UTF_8)
    assertEquals(lines.size, 1)
    assertJsonl(
      lines,
      parseJson(
        """{
          |  "runId": "00000000000000000000000000",
          |  "testId": "01ARZ3NDEKTSV4RRFFQ69G5FAW",
          |  "suiteName": "DiffResultJsonlReporterSpec$NestedDiffSuite",
          |  "suiteId": "difflicious.scalatest.DiffResultJsonlReporterSpec$NestedDiffSuite",
          |  "suiteClassName": "difflicious.scalatest.DiffResultJsonlReporterSpec$NestedDiffSuite",
          |  "testName": "outer scope inner scope leaf failure",
          |  "testText": "leaf failure",
          |  "testHierarchy": [
          |    "outer scope",
          |    "inner scope",
          |    "leaf failure"
          |  ],
          |  "fileName": "NestedDiffSuite.scala",
          |  "filePath": "/workspace/NestedDiffSuite.scala",
          |  "lineNumber": 64,
          |  "durationMillis": 0,
          |  "timeStamp": 0,
          |  "diffResult": {
          |    "type": "value",
          |    "valueType": "both",
          |    "obtained": "obtained",
          |    "expected": "expected",
          |    "isSame": false,
          |    "pairType": "both",
          |    "isIgnored": false,
          |    "isOk": false
          |  }
          |}""".stripMargin,
      ),
    )
  }

  test("keeps interleaved suite scopes independent") {
    val outputDir = Files.createTempDirectory("difflicious-scalatest-interleaved-scopes-jsonl")
    val reporter = zeroRunReporter(outputDir)
    val result = ValueResult.Both("1", "2", isSame = false, isIgnored = false)
    val suiteA = NameInfo("SuiteA", "suite-a", Some("example.SuiteA"), None)
    val suiteB = NameInfo("SuiteB", "suite-b", Some("example.SuiteB"), None)
    val suiteAFailure = DiffTestFailure(result, "SuiteA.scala", "/workspace/SuiteA.scala", 11)
    val suiteBFailure = DiffTestFailure(result, "SuiteB.scala", "/workspace/SuiteB.scala", 22)

    reporter(ScopeOpened(new Ordinal(0), "scope A", suiteA))
    reporter(ScopeOpened(new Ordinal(1), "scope B", suiteB))
    reporter(
      TestFailed(
        ordinal = new Ordinal(2),
        message = "diff failed",
        suiteName = "SuiteA",
        suiteId = "suite-a",
        suiteClassName = Some("example.SuiteA"),
        testName = "test A",
        testText = "leaf A",
        recordedEvents = IndexedSeq.empty,
        analysis = IndexedSeq.empty,
        throwable = Some(testFailedException(suiteAFailure)),
        duration = None,
        formatter = None,
        location = None,
        rerunner = None,
        payload = None,
        threadName = "main",
        timeStamp = 123L,
      ),
    )
    reporter(
      TestFailed(
        ordinal = new Ordinal(3),
        message = "diff failed",
        suiteName = "SuiteB",
        suiteId = "suite-b",
        suiteClassName = Some("example.SuiteB"),
        testName = "test B",
        testText = "leaf B",
        recordedEvents = IndexedSeq.empty,
        analysis = IndexedSeq.empty,
        throwable = Some(testFailedException(suiteBFailure)),
        duration = None,
        formatter = None,
        location = None,
        rerunner = None,
        payload = None,
        threadName = "main",
        timeStamp = 124L,
      ),
    )

    val runDirectory = outputDir.resolve(ZeroRunDirectory)
    val suiteALines = Files.readAllLines(runDirectory.resolve("example.SuiteA.jsonl"), StandardCharsets.UTF_8)
    val suiteBLines = Files.readAllLines(runDirectory.resolve("example.SuiteB.jsonl"), StandardCharsets.UTF_8)

    assertEquals(suiteALines.size, 1)
    assertEquals(suiteBLines.size, 1)
    assertJsonl(
      suiteALines,
      parseJson(
        """{
          |  "runId": "00000000000000000000000000",
          |  "testId": "01ARZ3NDEKTSV4RRFFQ69G5FAW",
          |  "suiteName": "SuiteA",
          |  "suiteId": "suite-a",
          |  "suiteClassName": "example.SuiteA",
          |  "testName": "test A",
          |  "testText": "leaf A",
          |  "testHierarchy": [
          |    "scope A",
          |    "leaf A"
          |  ],
          |  "fileName": "SuiteA.scala",
          |  "filePath": "/workspace/SuiteA.scala",
          |  "lineNumber": 11,
          |  "durationMillis": null,
          |  "timeStamp": 123,
          |  "diffResult": {
          |    "type": "value",
          |    "valueType": "both",
          |    "obtained": "1",
          |    "expected": "2",
          |    "isSame": false,
          |    "pairType": "both",
          |    "isIgnored": false,
          |    "isOk": false
          |  }
          |}""".stripMargin,
      ),
    )
    assertJsonl(
      suiteBLines,
      parseJson(
        """{
          |  "runId": "00000000000000000000000000",
          |  "testId": "01ARZ3NDEKTSV4RRFFQ69G5FAX",
          |  "suiteName": "SuiteB",
          |  "suiteId": "suite-b",
          |  "suiteClassName": "example.SuiteB",
          |  "testName": "test B",
          |  "testText": "leaf B",
          |  "testHierarchy": [
          |    "scope B",
          |    "leaf B"
          |  ],
          |  "fileName": "SuiteB.scala",
          |  "filePath": "/workspace/SuiteB.scala",
          |  "lineNumber": 22,
          |  "durationMillis": null,
          |  "timeStamp": 124,
          |  "diffResult": {
          |    "type": "value",
          |    "valueType": "both",
          |    "obtained": "1",
          |    "expected": "2",
          |    "isSame": false,
          |    "pairType": "both",
          |    "isIgnored": false,
          |    "isOk": false
          |  }
          |}""".stripMargin,
      ),
    )
  }

  private final class DiffSuite extends AnyFunSuite {
    test("testDiffFailure") {
      val result = ValueResult.Both("obtained", "expected", isSame = false, isIgnored = false)
      implicit val pos: Position = Position("DiffSuite.scala", "/workspace/DiffSuite.scala", 41)

      ScalatestDiffAssertions.failWithDiffResult(result)
    }
  }

  private final class NestedDiffSuite extends AnyFreeSpec {
    "outer scope" - {
      "inner scope" - {
        "leaf failure" in {
          val result = ValueResult.Both("obtained", "expected", isSame = false, isIgnored = false)
          implicit val pos: Position =
            Position("NestedDiffSuite.scala", "/workspace/NestedDiffSuite.scala", 64)

          ScalatestDiffAssertions.failWithDiffResult(result)
        }
      }
    }
  }

  private def testFailedException(diffFailure: DiffTestFailure): TestFailedException =
    new TestFailedException("diff failed", diffFailure, 0)

  private def jsonStringField(line: String, fieldName: String): String = {
    val prefix = "\"" + fieldName + "\":\""
    val start = line.indexOf(prefix)
    assert(start >= 0, s"Could not find JSON string field $fieldName in $line")
    val valueStart = start + prefix.length
    val valueEnd = line.indexOf('"', valueStart)
    assert(valueEnd >= valueStart, s"Could not read JSON string field $fieldName in $line")
    line.substring(valueStart, valueEnd)
  }

  private def zeroRunReporter(outputDir: Path): DiffResultJsonlReporter =
    new DiffResultJsonlReporter(
      outputDir.toString,
      DiffliciousResultDefaults.ZeroUlid,
      deterministicUlidGenerator(),
    )

  private def assertJsonl(lines: java.util.List[String], expected: Json*): Unit =
    assertEquals(lines.asScala.map(line => normalizeScalatestFields(parseJson(line))).toSeq, expected)

  private def normalizeScalatestFields(json: Json): Json = {
    val suiteName = json.hcursor.get[String]("suiteName").toOption

    if (suiteName.exists(_.startsWith("DiffResultJsonlReporterSpec$")))
      json.mapObject(
        _.add("durationMillis", Json.fromInt(0))
          .add("timeStamp", Json.fromInt(0)),
      )
    else json
  }

  private def deterministicUlidGenerator(): UlidGenerator = new UlidGenerator {
    private val testIds = Iterator(TestId, SecondTestId)

    override def generate(): String =
      if (testIds.hasNext) testIds.next()
      else fail("Deterministic ULID generator exhausted")
  }

  private def parseJson(value: String): Json =
    parse(value).fold(error => fail(s"Invalid JSON: ${error.message}"), identity)

  private def listJsonlFiles(directory: Path): List[Path] = {
    val stream = Files.walk(directory)
    try
      stream
        .iterator()
        .asScala
        .filter(path => Files.isRegularFile(path) && path.getFileName.toString.endsWith(".jsonl"))
        .toList
    finally stream.close()
  }
}

package difflicious.scalatest

import difflicious.DiffResult
import difflicious.DiffResult.ValueResult
import difflicious.reporter.DiffResultJsonlWriter
import difflicious.reporter.DiffliciousResultDefaults
import difflicious.reporter.UlidGenerator
import io.circe.Json
import io.circe.parser.parse
import munit.FunSuite
import org.scalactic.source.Position
import org.scalatest.Args
import org.scalatest.events.{NameInfo, Ordinal, ScopeOpened, TestFailed}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.funsuite.AnyFunSuite

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

class DiffResultJsonlReporterSpec extends FunSuite {
  private val TestId = "01ARZ3NDEKTSV4RRFFQ69G5FAW"
  private val SecondTestId = "01ARZ3NDEKTSV4RRFFQ69G5FAX"
  private val ZeroRunDirectory = DiffliciousResultDefaults.ZeroUlid

  test("assertion failure is a ScalaTest difference exception") {
    val result = ValueResult.Both("1", "2", isSame = false, isIgnored = false)
    implicit val pos: Position = Position("ExampleSuite.scala", "/workspace/ExampleSuite.scala", 37)

    val failure = intercept[ScalatestDifferenceFoundException] {
      ScalatestDiffAssertions.failWithDiffResult(result)
    }

    assertEquals(failure.diffResult, result)
    assertEquals(failure.fileName, "ExampleSuite.scala")
    assertEquals(failure.filePath, "/workspace/ExampleSuite.scala")
    assertEquals(failure.lineNumber, 37)
    assertEquals(failure.getCause, null)
    assertEquals(failure.payload, None)
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
    val suiteAFailure = differenceFoundException(result, "SuiteA.scala", "/workspace/SuiteA.scala", 11)
    val suiteBFailure = differenceFoundException(result, "SuiteB.scala", "/workspace/SuiteB.scala", 22)

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
        throwable = Some(suiteAFailure),
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
        throwable = Some(suiteBFailure),
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

  private def differenceFoundException(
    result: DiffResult,
    fileName: String,
    filePath: String,
    lineNumber: Int,
  ): ScalatestDifferenceFoundException = {
    implicit val pos: Position = Position(fileName, filePath, lineNumber)
    new ScalatestDifferenceFoundException(result)
  }

  private def zeroRunReporter(outputDir: Path): DiffResultJsonlReporter =
    new DiffResultJsonlReporter(
      new DiffResultJsonlWriter(
        outputDir.toString,
        DiffliciousResultDefaults.ZeroUlid,
        deterministicUlidGenerator(),
      ),
    )

  private def assertJsonl(lines: java.util.List[String], expected: Json*): Unit =
    assertEquals(lines.asScala.map(parseJson).toSeq, expected)

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

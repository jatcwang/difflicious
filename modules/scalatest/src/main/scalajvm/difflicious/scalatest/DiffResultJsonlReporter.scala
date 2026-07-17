package difflicious.scalatest

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import difflicious.reporter.DiffliciousResultDefaults
import difflicious.reporter.DiffResultTestDetails
import difflicious.reporter.UlidGenerator
import org.scalatest.Reporter
import org.scalatest.events.{Event, ScopeClosed, ScopeOpened, SuiteAborted, SuiteCompleted, TestFailed}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

final class DiffResultJsonlReporter private[scalatest] (
  outputDirectory: String,
  runId: String,
  ulidGenerator: UlidGenerator,
) extends Reporter {
  private[scalatest] def this(outputDirectory: String, runId: String) =
    this(outputDirectory, runId, UlidGenerator.Default)

  def this(outputDirectory: String) =
    this(outputDirectory, DiffResultJsonlReporter.configuredRunId, UlidGenerator.Default)

  def this() =
    this(
      sys.props.getOrElse(
        DiffliciousResultDefaults.OutputDirectoryProperty,
        DiffResultJsonlReporter.DefaultOutputDirectory,
      ),
      DiffResultJsonlReporter.configuredRunId,
      UlidGenerator.Default,
    )

  private val outputDir = Paths.get(outputDirectory)
  private val suiteScopes: concurrent.Map[String, Vector[String]] = TrieMap.empty

  override def apply(event: Event): Unit =
    event match {
      case scopeOpened: ScopeOpened =>
        updateSuiteScopes(scopeOpened.nameInfo.suiteId)(_ :+ scopeOpened.message)

      case scopeClosed: ScopeClosed =>
        updateSuiteScopes(scopeClosed.nameInfo.suiteId)(DiffResultJsonlReporter.popScope(_, scopeClosed.message))

      case testFailed: TestFailed =>
        DiffResultJsonlReporter.diffTestFailure(testFailed).foreach { diffFailure =>
          val record =
            DiffResultJsonlReporter.jsonlRecord(
              testFailed,
              diffFailure,
              hierarchyFor(testFailed),
              runId,
              ulidGenerator,
            )
          writeJsonLine(
            outputDir
              .resolve(DiffResultJsonlReporter.directoryNameForRun(record.runId, testFailed.timeStamp))
              .resolve(DiffResultJsonlReporter.fileNameForSuite(testFailed)),
            record,
          )
        }

      case suiteCompleted: SuiteCompleted =>
        removeSuiteScopes(suiteCompleted.suiteId)

      case suiteAborted: SuiteAborted =>
        removeSuiteScopes(suiteAborted.suiteId)

      case _ =>
    }

  private def hierarchyFor(event: TestFailed): Vector[String] = {
    val scopes = suiteScopes.getOrElse(event.suiteId, Vector.empty)
    if (scopes.nonEmpty) scopes :+ event.testText else Vector(event.testName)
  }

  private def updateSuiteScopes(suiteId: String)(update: Vector[String] => Vector[String]): Unit = {
    val _ = suiteScopes.updateWith(suiteId) { currentScopes =>
      Some(update(currentScopes.getOrElse(Vector.empty)))
    }
    ()
  }

  private def removeSuiteScopes(suiteId: String): Unit = {
    val _ = suiteScopes.remove(suiteId)
    ()
  }

  private def writeJsonLine(file: Path, record: DiffResultTestDetails): Unit = {
    Files.createDirectories(file.getParent)
    Files.write(
      file,
      DiffResultJsonlReporter.jsonLineBytes(record),
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND,
    )
    ()
  }
}

object DiffResultJsonlReporter {
  val DefaultOutputDirectory = DiffliciousResultDefaults.OutputDirectory
  private val TimestampDirectoryFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)

  private def configuredRunId: String =
    sys.props.getOrElse(DiffliciousResultDefaults.RunIdProperty, DiffliciousResultDefaults.ZeroUlid)

  private[scalatest] def diffTestFailure(event: TestFailed): Option[DiffTestFailure] =
    event.throwable.flatMap(throwable =>
      Option(throwable.getCause).collect { case diffFailure: DiffTestFailure => diffFailure },
    )

  private[scalatest] def fileNameForSuite(event: TestFailed): String = {
    val suiteIdentifier = event.suiteClassName.getOrElse(event.suiteId)
    s"${sanitizeFileName(suiteIdentifier)}.jsonl"
  }

  private[scalatest] def directoryNameForRun(runId: String, timeStamp: Long): String =
    if (runId == DiffliciousResultDefaults.ZeroUlid)
      TimestampDirectoryFormatter.format(Instant.ofEpochMilli(timeStamp))
    else runId

  private def jsonlRecord(
    event: TestFailed,
    diffFailure: DiffTestFailure,
    testHierarchy: Seq[String],
    runId: String,
    ulidGenerator: UlidGenerator,
  ): DiffResultTestDetails =
    DiffResultTestDetails(
      runId = runId,
      testId = ulidGenerator.generate(),
      suiteName = event.suiteName,
      suiteId = event.suiteId,
      suiteClassName = event.suiteClassName,
      testName = event.testName,
      testText = event.testText,
      testHierarchy = testHierarchy.toVector,
      fileName = diffFailure.fileName,
      filePath = diffFailure.filePath,
      lineNumber = diffFailure.lineNumber,
      durationMillis = event.duration,
      timeStamp = event.timeStamp,
      diffResult = diffFailure.diffResult,
    )

  private def jsonLineBytes(record: DiffResultTestDetails): Array[Byte] = {
    val jsonBytes = writeToArray(record)
    val lineSeparatorBytes = System.lineSeparator().getBytes(StandardCharsets.UTF_8)
    val lineBytes = java.util.Arrays.copyOf(jsonBytes, jsonBytes.length + lineSeparatorBytes.length)
    System.arraycopy(lineSeparatorBytes, 0, lineBytes, jsonBytes.length, lineSeparatorBytes.length)
    lineBytes
  }

  private[scalatest] def popScope(scopes: Vector[String], scope: String): Vector[String] =
    scopes.lastIndexOf(scope) match {
      case -1 => scopes
      case index => scopes.take(index)
    }

  private def sanitizeFileName(value: String): String = {
    val sanitized = value.map {
      case ch if ch.isLetterOrDigit || ch == '.' || ch == '-' || ch == '_' => ch
      case _ => '_'
    }.mkString

    if (sanitized.nonEmpty) sanitized else "unknown-suite"
  }

}

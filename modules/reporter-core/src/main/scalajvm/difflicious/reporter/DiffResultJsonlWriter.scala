package difflicious.reporter

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}

final class DiffResultJsonlWriter private[difflicious] (
  outputDirectory: String,
  runId: String,
) {
  def this(outputDirectory: String) =
    this(outputDirectory, DiffResultJsonlWriter.configuredRunId)

  def this() =
    this(
      sys.props.getOrElse(
        DiffliciousResultDefaults.OutputDirectoryProperty,
        DiffResultJsonlWriter.DefaultOutputDirectory,
      ),
      DiffResultJsonlWriter.configuredRunId,
    )

  private val outputDir = Paths.get(outputDirectory)

  def write(
    suiteName: String,
    suiteId: String,
    suiteClassName: Option[String],
    testName: String,
    testText: String,
    testHierarchy: Vector[String],
  )(failure: DifferenceFound): Unit = synchronized {
    val record = DiffResultTestDetails(
      runId = runId,
      testId = failure.testId,
      suiteName = suiteName,
      suiteId = suiteId,
      suiteClassName = suiteClassName,
      testName = testName,
      testText = testText,
      testHierarchy = testHierarchy,
      fileName = failure.fileName,
      filePath = failure.filePath,
      lineNumber = failure.lineNumber,
      diffResult = failure.diffResult,
    )
    val file = outputDir
      .resolve(record.runId)
      .resolve(DiffResultJsonlWriter.fileNameForSuite(suiteClassName, suiteId))

    Files.createDirectories(file.getParent)
    Files.write(
      file,
      DiffResultJsonlWriter.jsonLineBytes(record),
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND,
    )
    ()
  }
}

object DiffResultJsonlWriter {
  val DefaultOutputDirectory = DiffliciousResultDefaults.OutputDirectory

  private def configuredRunId: String =
    sys.props.getOrElse(DiffliciousResultDefaults.RunIdProperty, DiffliciousResultDefaults.ZeroUlid)

  private[difflicious] def fileNameForSuite(suiteClassName: Option[String], suiteId: String): String =
    s"${sanitizeFileName(suiteClassName.getOrElse(suiteId))}.jsonl"

  private def jsonLineBytes(record: DiffResultTestDetails): Array[Byte] = {
    val jsonBytes = writeToArray(record)
    val lineSeparatorBytes = System.lineSeparator().getBytes(StandardCharsets.UTF_8)
    val lineBytes = java.util.Arrays.copyOf(jsonBytes, jsonBytes.length + lineSeparatorBytes.length)
    System.arraycopy(lineSeparatorBytes, 0, lineBytes, jsonBytes.length, lineSeparatorBytes.length)
    lineBytes
  }

  private def sanitizeFileName(value: String): String = {
    val sanitized = value.map {
      case ch if ch.isLetterOrDigit || ch == '.' || ch == '-' || ch == '_' => ch
      case _ => '_'
    }.mkString

    if (sanitized.nonEmpty) sanitized else "unknown-suite"
  }
}

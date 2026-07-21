package difflicious.cli

import difflicious.reporter.DiffResultTestDetails

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

object ReporterJsonlReader {
  def read(input: CliInput.Report, stdin: InputStream, mode: RunMode): Either[String, DiffReport] =
    if (mode == RunMode.Tui && input.paths.contains("-"))
      Left("TUI mode cannot read a JSONL report from stdin because stdin is used for interactive commands.")
    else
      for {
        lines <- readLines(input.paths, stdin)
        runs <- decodeLines(lines)
      } yield DiffReport(runs)

  private def readLines(paths: Vector[String], stdin: InputStream): Either[String, Vector[JsonlLine]] =
    collectWithBuilder(paths)(path => readPath(path, stdin))

  private def readPath(path: String, stdin: InputStream): Either[String, Vector[JsonlLine]] =
    if (path == "-") readStdin(stdin)
    else {
      val file = Paths.get(path)
      try {
        if (Files.isDirectory(file)) readDirectory(file)
        else readFile(file)
      } catch {
        case NonFatal(error) => Left(s"Could not read JSONL report '$path': ${error.getMessage}")
      }
    }

  private def readDirectory(directory: Path): Either[String, Vector[JsonlLine]] = {
    val stream = Files.walk(directory)
    try {
      val files =
        stream
          .iterator()
          .asScala
          .filter(path => Files.isRegularFile(path) && path.getFileName.toString.endsWith(".jsonl"))
          .toVector
          .sortBy(_.toString)

      collectWithBuilder(files)(readFile)
    } finally {
      stream.close()
    }
  }

  private def readFile(file: Path): Either[String, Vector[JsonlLine]] =
    try {
      val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
      Right(numberedLines(file.toString, lines))
    } catch {
      case NonFatal(error) => Left(s"Could not read JSONL report '${file.toString}': ${error.getMessage}")
    }

  private def readStdin(stdin: InputStream): Either[String, Vector[JsonlLine]] =
    try {
      val text = new String(stdin.readAllBytes(), StandardCharsets.UTF_8)
      Right(numberedLines("<stdin>", text.linesIterator.toVector))
    } catch {
      case NonFatal(error) => Left(s"Could not read JSONL report from stdin: ${error.getMessage}")
    }

  private def numberedLines(source: String, lines: Vector[String]): Vector[JsonlLine] =
    lines.zipWithIndex.collect {
      case (line, index) if line.trim.nonEmpty => JsonlLine(source, index + 1, line)
    }

  private def decodeLines(lines: Vector[JsonlLine]): Either[String, Vector[DiffRun]] =
    traverseWithBuilder(lines)(decodeLine)

  private def decodeLine(line: JsonlLine): Either[String, DiffRun] =
    try Right(toRun(DiffResultTestDetails.fromJsonString(line.text)))
    catch {
      case NonFatal(error) =>
        Left(s"${line.source}:${line.number}: invalid Difflicious JSONL record: ${error.getMessage}")
    }

  private def toRun(record: DiffResultTestDetails): DiffRun =
    DiffRun.fromResult(
      record.diffResult,
      Some(
        DiffRunMetadata(
          runId = record.runId,
          testId = record.testId,
          suiteName = record.suiteName,
          suiteId = record.suiteId,
          suiteClassName = record.suiteClassName,
          testName = record.testName,
          testText = record.testText,
          testHierarchy = record.testHierarchy,
          fileName = record.fileName,
          filePath = record.filePath,
          lineNumber = record.lineNumber,
        ),
      ),
    )

  private def collectWithBuilder[A, B](
    values: Vector[A],
  )(read: A => Either[String, Vector[B]]): Either[String, Vector[B]] = {
    val builder = Vector.newBuilder[B]
    values
      .foldLeft[Either[String, Unit]](Right(())) {
        case (Left(error), _) => Left(error)
        case (Right(_), value) =>
          read(value).map { next =>
            builder ++= next
            ()
          }
      }
      .map(_ => builder.result())
  }

  private def traverseWithBuilder[A, B](values: Vector[A])(read: A => Either[String, B]): Either[String, Vector[B]] = {
    val builder = Vector.newBuilder[B]
    values
      .foldLeft[Either[String, Unit]](Right(())) {
        case (Left(error), _) => Left(error)
        case (Right(_), value) =>
          read(value).map { next =>
            builder += next
            ()
          }
      }
      .map(_ => builder.result())
  }

  private final case class JsonlLine(source: String, number: Int, text: String)
}

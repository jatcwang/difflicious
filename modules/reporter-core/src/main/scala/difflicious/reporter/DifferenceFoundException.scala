package difflicious.reporter

import difflicious.{DiffResult, DiffResultPrinter}

import scala.annotation.tailrec

trait DifferenceFound {
  private lazy val generatedTestId = UlidGenerator.Default.generate()

  def testId: String = generatedTestId
  def diffResult: DiffResult
  def fileName: String
  def filePath: String
  def lineNumber: Int
}

final case class DifferenceFoundException(
  diffResult: DiffResult,
  fileName: String,
  filePath: String,
  lineNumber: Int,
  override val testId: String = UlidGenerator.Default.generate(),
) extends RuntimeException(DifferenceFound.message(testId, diffResult))
    with DifferenceFound

object DifferenceFound {
  private[difflicious] def message(testId: String, diffResult: DiffResult): String =
    s"Test id: $testId\n${DiffResultPrinter.consoleOutput(diffResult, 0).render}"

  @tailrec
  def fromThrowable(throwable: Throwable): Option[DifferenceFound] =
    throwable match {
      case failure: DifferenceFound => Some(failure)
      case other =>
        Option(other.getCause) match {
          case Some(cause) => fromThrowable(cause)
          case None => None
        }
    }
}

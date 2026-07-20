package difflicious.reporter

import difflicious.{DiffResult, DiffResultPrinter}

import scala.annotation.tailrec

trait DifferenceFound {
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
) extends RuntimeException(DiffResultPrinter.consoleOutput(diffResult, 0).render)
    with DifferenceFound

object DifferenceFound {
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

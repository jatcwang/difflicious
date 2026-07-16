package difflicious.scalatest

import difflicious.{DiffResult, DiffResultPrinter}
import org.scalactic.source.Position
import org.scalatest.exceptions.{StackDepthException, TestFailedException}

import scala.util.control.NoStackTrace

final case class DiffTestFailure(
  diffResult: DiffResult,
  fileName: String,
  filePath: String,
  lineNumber: Int,
) extends RuntimeException("Difflicious diff assertion failed")
    with NoStackTrace

private[scalatest] object ScalatestDiffAssertions {
  def failWithDiffResult(diffResult: DiffResult)(implicit pos: Position): Nothing = {
    val message = DiffResultPrinter.consoleOutput(diffResult, 0).render
    val diffFailure = DiffTestFailure(
      diffResult = diffResult,
      fileName = pos.fileName,
      filePath = pos.filePathname,
      lineNumber = pos.lineNumber,
    )

    throw new TestFailedException(
      (_: StackDepthException) => Some(message),
      Some(diffFailure),
      pos,
      None,
    )
  }
}

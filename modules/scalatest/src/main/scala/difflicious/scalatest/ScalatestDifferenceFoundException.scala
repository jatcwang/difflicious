package difflicious.scalatest

import difflicious.{DiffResult, DiffResultPrinter}
import difflicious.reporter.DifferenceFound
import org.scalactic.source.Position
import org.scalatest.exceptions.{StackDepthException, TestFailedException}

final class ScalatestDifferenceFoundException(
  val diffResult: DiffResult,
)(implicit pos: Position)
    extends TestFailedException(
      (_: StackDepthException) => Some(DiffResultPrinter.consoleOutput(diffResult, 0).render),
      None,
      pos,
      None,
    )
    with DifferenceFound {
  override val fileName: String = pos.fileName
  override val filePath: String = pos.filePathname
  override val lineNumber: Int = pos.lineNumber
}

private[scalatest] object ScalatestDiffAssertions {
  def failWithDiffResult(diffResult: DiffResult)(implicit pos: Position): Nothing =
    throw new ScalatestDifferenceFoundException(diffResult)
}

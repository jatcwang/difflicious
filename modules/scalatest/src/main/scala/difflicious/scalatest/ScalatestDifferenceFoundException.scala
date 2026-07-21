package difflicious.scalatest

import difflicious.DiffResult
import difflicious.reporter.{DifferenceFound, UlidGenerator}
import org.scalactic.source.Position
import org.scalatest.exceptions.{StackDepthException, TestFailedException}

final class ScalatestDifferenceFoundException(
  val diffResult: DiffResult,
  override val testId: String = UlidGenerator.Default.generate(),
)(implicit pos: Position)
    extends TestFailedException(
      (_: StackDepthException) => Some(DifferenceFound.message(testId, diffResult)),
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
  def failWithDiffResult(
    diffResult: DiffResult,
    testId: String = UlidGenerator.Default.generate(),
  )(implicit pos: Position): Nothing =
    throw new ScalatestDifferenceFoundException(diffResult, testId)
}

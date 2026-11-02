package difflicious.ziotest

import difflicious.reporter.{DifferenceFound, DiffResultJsonlWriter}
import zio.{Chunk, UIO, ZIO}
import zio.test.{TestAspectAtLeastR, TestAspectPoly, TestEnvironment}

trait ZIODiffliciousSpec extends ZIODiffliciousSpecBase {
  private lazy val diffliciousJsonlWriter = new DiffResultJsonlWriter()

  override def aspects: Chunk[TestAspectAtLeastR[Environment & TestEnvironment]] =
    super.aspects :+ diffliciousReporting

  override protected def diffliciousReporting: TestAspectPoly =
    ZIODiffliciousSpecBase.reportingAspect(reportDiffFailure)

  private def reportDiffFailure(testHierarchy: Vector[String], failure: DifferenceFound): UIO[Unit] = {
    val className = getClass.getName.stripSuffix("$")
    val suiteName = className.split('.').lastOption.getOrElse(className)
    val testName = testHierarchy.lastOption.getOrElse("")
    ZIO
      .attemptBlocking {
        diffliciousJsonlWriter.write(
          suiteName = suiteName,
          suiteId = className,
          suiteClassName = Some(className),
          testName = testName,
          testText = testName,
          testHierarchy = ZIODiffliciousSpecBase.withoutSpecName(suiteName, testHierarchy),
        )(failure)
      }
      .catchAllCause(cause => ZIO.logWarningCause("Failed to write Difflicious diff report", cause))
  }
}

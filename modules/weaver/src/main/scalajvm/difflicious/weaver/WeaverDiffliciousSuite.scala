package difflicious.weaver

import difflicious.Differ
import difflicious.reporter.{DifferenceFound, DifferenceFoundException, DiffResultJsonlWriter}
import weaver.{Expectations, FSuite, Log, TestName}
import weaver.Expectations.Helpers.success

trait WeaverDiffliciousSuite[F[_]] extends FSuite[F] {
  implicit class DifferExtensions[A](differ: Differ[A]) {
    def assertNoDiff(obtained: A, expected: A): Expectations = {
      val result = differ.diff(obtained, expected)
      if (!result.isOk)
        throw DifferenceFoundException(result, "", "", 0)
      else success
    }
  }

  private lazy val diffliciousJsonlWriter = new DiffResultJsonlWriter()

  override def pureTest(name: TestName)(run: => Expectations): Unit =
    super.pureTest(name) {
      try run
      catch {
        case failure: DifferenceFoundException =>
          reportDiffFailure(name, failure)
          throw failure
      }
    }

  override def loggedTest(name: TestName)(run: Log[F] => F[Expectations]): Unit =
    super.loggedTest(name)(log => reportedEffect(name)(run(log)))

  override def test(name: TestName): PartiallyAppliedTest =
    new ReportingPartiallyAppliedTest(name)

  final class ReportingPartiallyAppliedTest(name: TestName) extends PartiallyAppliedTest(name) {
    override def apply(run: => F[Expectations]): Unit =
      super.apply(reportedEffect(name)(run))

    override def apply(run: Res => F[Expectations]): Unit =
      super.apply((resource: Res) => reportedEffect(name)(run(resource)))

    override def apply(run: (Res, Log[F]) => F[Expectations]): Unit =
      super.apply((resource: Res, log: Log[F]) => reportedEffect(name)(run(resource, log)))

    override def usingRes(run: Res => F[Expectations]): Unit = apply(run)
  }

  private def reportedEffect(name: TestName)(run: => F[Expectations]): F[Expectations] =
    effect.defer {
      effect.handleErrorWith(effect.defer(run)) { error =>
        DifferenceFound.fromThrowable(error) match {
          case Some(failure) =>
            effect.flatMap(effect.blocking(reportDiffFailure(name, failure))) { _ =>
              effect.raiseError(error)
            }
          case None => effect.raiseError(error)
        }
      }
    }

  private def reportDiffFailure(name: TestName, failure: DifferenceFound): Unit = {
    val className = getClass.getName.stripSuffix("$")
    diffliciousJsonlWriter.write(
      suiteName = suiteName,
      suiteId = className,
      suiteClassName = Some(className),
      testName = name.name,
      testText = name.name,
      testHierarchy = Vector(name.name),
    )(withInferredLocation(failure))
  }

  private def withInferredLocation(failure: DifferenceFound): DifferenceFound =
    failure match {
      case exception: DifferenceFoundException
          if exception.fileName.isEmpty || exception.filePath.isEmpty || exception.lineNumber <= 0 =>
        val location = exception.getStackTrace.find { frame =>
          !frame.getClassName.startsWith("difflicious.weaver.") &&
          !frame.getClassName.startsWith("difflicious.reporter.DifferenceFoundException")
        }
        val fileName = location.flatMap(frame => Option(frame.getFileName)).getOrElse("")
        exception.copy(
          fileName = fileName,
          filePath = fileName,
          lineNumber = location.map(_.getLineNumber).getOrElse(0),
        )
      case located => located
    }
}

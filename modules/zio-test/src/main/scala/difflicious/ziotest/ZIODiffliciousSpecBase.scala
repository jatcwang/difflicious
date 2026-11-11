package difflicious.ziotest

import difflicious.{Differ, DiffResult}
import difflicious.reporter.DifferenceFound
import zio.{Chunk, Trace, UIO, ZIO}
import zio.internal.stacktracer.SourceLocation
import zio.test.{
  assertCompletes,
  ErrorMessage,
  Spec,
  TestArrow,
  TestAspectPoly,
  TestFailure,
  TestResult,
  TestTrace,
  ZIOSpecAbstract,
}

private[ziotest] trait ZIODiffliciousSpecBase extends ZIOSpecAbstract with ZIOInstances {
  implicit class DifferExtensions[A](differ: Differ[A]) {
    def assertNoDiff(obtained: A, expected: A)(implicit trace: Trace, sourceLocation: SourceLocation): TestResult =
      ZIODiffliciousSpecBase.assertNoDiff(differ, obtained, expected)
  }

  /** Writes reports for failed diffs. Include this when overriding `aspects` without calling `super.aspects`. */
  protected def diffliciousReporting: TestAspectPoly
}

private[ziotest] object ZIODiffliciousSpecBase {
  def assertNoDiff[A](differ: Differ[A], obtained: A, expected: A)(implicit
    trace: Trace,
    sourceLocation: SourceLocation,
  ): TestResult =
    differ.equalsOrDiff(obtained, expected) match {
      case Some(result) if !result.isOk =>
        val path = sourceLocation.path
        val fileName = path.substring(math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1)
        val marker = new DiffMarker(ZIODifferenceFound(result, fileName, path, sourceLocation.line))
        TestResult(
          TestArrow
            .make[Any, Boolean](_ => TestTrace.succeed(marker) >>> TestTrace.fail(marker.message))
            .withCode(DiffMarker.code)
            .withLocation,
        )
      case _ => assertCompletes
    }

  // zio-test builds tests as a tree of plain values, so the only place that sees each test together with the labels
  // of its enclosing suites is an aspect applied to the whole spec
  def reportingAspect(report: (Vector[String], DifferenceFound) => UIO[Unit]): TestAspectPoly =
    new TestAspectPoly {
      override def some[R, E](spec: Spec[R, E])(implicit trace: Trace): Spec[R, E] =
        withReporting(Vector.empty, spec, report)
    }

  private def withReporting[R, E](
    testHierarchy: Vector[String],
    spec: Spec[R, E],
    report: (Vector[String], DifferenceFound) => UIO[Unit],
  )(implicit trace: Trace): Spec[R, E] =
    spec.caseValue match {
      case Spec.ExecCase(exec, spec) => Spec.exec(exec, withReporting(testHierarchy, spec, report))
      case Spec.LabeledCase(label, spec) => Spec.labeled(label, withReporting(testHierarchy :+ label, spec, report))
      case Spec.ScopedCase(scoped) => Spec.scoped[R](scoped.map(withReporting(testHierarchy, _, report)))
      case Spec.MultipleCase(specs) => Spec.multiple(specs.map(withReporting(testHierarchy, _, report)))
      case Spec.TestCase(test, annotations) =>
        val reportedTest = test.tapError {
          case TestFailure.Assertion(result, _) => ZIO.foreachDiscard(diffsIn(result))(report(testHierarchy, _))
          case _ => ZIO.unit
        }
        Spec.test(reportedTest, annotations)
    }

  // A spec's root suite is often labelled with the spec's own name, which the report already records as `suiteName`,
  // so the viewer would show it twice. A lone label is the test itself and stays.
  def withoutSpecName(suiteName: String, testHierarchy: Vector[String]): Vector[String] =
    if (testHierarchy.size > 1 && testHierarchy.head == suiteName) testHierarchy.tail else testHierarchy

  // `failures` is zio-test's own cut of the result down to what made the test fail, and is what its console prints,
  // so a diff is reported exactly when it is shown as a cause of the failure.
  def diffsIn(result: TestResult): Chunk[DifferenceFound] =
    Chunk.fromIterable(result.failures.toList.flatMap(_.values)).collect { case marker: DiffMarker => marker.found }

  private final case class ZIODifferenceFound(
    diffResult: DiffResult,
    fileName: String,
    filePath: String,
    lineNumber: Int,
  ) extends DifferenceFound

  // Holds the diff as the value of a passed step just before the failed check,
  // since zio-test keeps a step's predecessors whenever it keeps the step.
  // zio-test prints a step's value unless it matches the step's code,
  // so toString returns the code to keep the marker out of the output.
  // Not a case class, which would print field by field.
  private final class DiffMarker(val found: DifferenceFound) {
    lazy val message: ErrorMessage = ErrorMessage.custom(DifferenceFound.message(found.testId, found.diffResult))

    override def toString: String = DiffMarker.code
  }

  private object DiffMarker {
    val code = "assertNoDiff(obtained, expected)"
  }
}

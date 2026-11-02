package difflicious.ziotest

import difflicious.reporter.DifferenceFound
import difflicious.utils.TypeName
import difflicious.{ConfigureError, ConfigureOp, ConfigurePath, Differ, DiffInput, DiffResult}
import zio.{Chunk, ExecutionStrategy, Ref, Scope, ZIO, ZLayer}
import zio.internal.stacktracer.SourceLocation
import zio.test.{
  assertCompletes,
  assertTrue,
  check,
  Gen,
  Spec,
  TestAspect,
  TestEnvironment,
  TestFailure,
  TestSuccess,
  ZIOSpecDefault,
}
import zio.test.render.ConsoleRenderer

object ZIODiffliciousSpecSpec extends ZIOSpecDefault with ZIODiffliciousSpec {
  private val intTypeName = TypeName[Int]
  private val ints = Differ.useEquals[Int](_.toString)

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ZIODiffliciousSpec")(
      suite("assertNoDiff")(
        test("succeeds when values are equal") {
          ints.assertNoDiff(1, 1)
        },
        test("fails with the diff when values differ") {
          val result = ints.assertNoDiff(1, 2)
          assertTrue(
            result.isFailure,
            ZIODiffliciousSpecBase.diffsIn(result).map(_.diffResult) == Chunk(ints.diff(1, 2)),
          )
        },
        test("records where it was called") {
          val (result, callSite) = (ints.assertNoDiff(1, 2), implicitly[SourceLocation])
          val found = ZIODiffliciousSpecBase.diffsIn(result)
          assertTrue(
            found.map(_.fileName) == Chunk("ZIODiffliciousSpecSpec.scala"),
            found.map(_.filePath) == Chunk(callSite.path),
            found.map(_.lineNumber) == Chunk(callSite.line),
          )
        },
        test("adds no line of its own to the failure output") {
          val failures = ints.assertNoDiff(1, 2).failures.toList
          val lines =
            failures.flatMap(f => ConsoleRenderer.renderToStringLines(ConsoleRenderer.renderAssertionResult(f, 0)))
          assertTrue(lines.nonEmpty, !lines.exists(_.contains("assertNoDiff(obtained, expected) =")))
        },
        test("skips diff when canUseEquals is true and values are equal") {
          var diffCalled = false
          val differ = trackingDiffer(canUseEqualsValue = true) {
            diffCalled = true
            DiffResult.ValueResult.Both(intTypeName, "1", "1", isSame = true, isIgnored = false)
          }
          val result = differ.assertNoDiff(1, 1)
          assertTrue(result.isSuccess, !diffCalled)
        },
        test("still diffs equal values when canUseEquals is false") {
          var diffCalled = false
          val differ = trackingDiffer(canUseEqualsValue = false) {
            diffCalled = true
            DiffResult.ValueResult.Both(intTypeName, "1", "1", isSame = true, isIgnored = false)
          }
          val result = differ.assertNoDiff(1, 1)
          assertTrue(result.isSuccess, diffCalled)
        },
      ),
      suite("reporting aspect")(
        test("reports a failed diff with the labels of every enclosing suite") {
          runReported(suite("outer")(suite("inner")(test("fails")(ints.assertNoDiff(1, 2))))).map { run =>
            assertTrue(
              run.results.map(_.isLeft) == Chunk(true),
              run.reports.map(_._1) == Chunk(Vector("outer", "inner", "fails")),
              run.reportedDiffs == Chunk(ints.diff(1, 2)),
            )
          }
        },
        test("keeps the labels of suites with a shared layer or their own execution strategy") {
          val tests = suite("outer")(
            suite("shared layer")(test("fails")(ZIO.service[Int].map(ints.assertNoDiff(_, 2))))
              .provideLayerShared(ZLayer.succeed(1)),
            suite("sequential")(test("fails")(ints.assertNoDiff(3, 4))) @@ TestAspect.sequential,
          )
          runReported(tests).map { run =>
            assertTrue(
              run.reports.map(_._1) == Chunk(
                Vector("outer", "shared layer", "fails"),
                Vector("outer", "sequential", "fails"),
              ),
            )
          }
        },
        test("leaves the failure as a plain assertion failure") {
          runReported(test("fails")(ints.assertNoDiff(1, 2))).map { run =>
            assertTrue(run.results.collect { case Left(_: TestFailure.Assertion) => true } == Chunk(true))
          }
        },
        test("reports diffs from effectful tests") {
          runReported(test("fails")(ZIO.yieldNow.as(ints.assertNoDiff(1, 2)))).map { run =>
            assertTrue(run.reports.map(_._1) == Chunk(Vector("fails")))
          }
        },
        test("reports a diff that ends a for comprehension early") {
          val failsEarly = test("fails") {
            for {
              _ <- ints.assertNoDiff(1, 2)
              _ <- ints.assertNoDiff(3, 4)
            } yield assertCompletes
          }
          runReported(failsEarly).map { run =>
            assertTrue(
              run.results.collect { case Left(_: TestFailure.Assertion) => true } == Chunk(true),
              run.reportedDiffs == Chunk(ints.diff(1, 2)),
            )
          }
        },
        test("reports every failed diff combined with &&") {
          runReported(test("fails")(ints.assertNoDiff(1, 2) && ints.assertNoDiff(3, 4))).map { run =>
            assertTrue(run.reportedDiffs == Chunk(ints.diff(1, 2), ints.diff(3, 4)))
          }
        },
        test("reports a diff combined with another failed assertion") {
          runReported(test("fails")(assertTrue(1 == 2) && ints.assertNoDiff(3, 4))).map { run =>
            assertTrue(run.reportedDiffs == Chunk(ints.diff(3, 4)))
          }
        },
        test("reports a failed diff combined with || when both sides fail") {
          runReported(test("fails")(ints.assertNoDiff(1, 2) || assertTrue(1 == 2))).map { run =>
            assertTrue(run.results.map(_.isLeft) == Chunk(true), run.reportedDiffs == Chunk(ints.diff(1, 2)))
          }
        },
        test("does not report a diff under !, where it makes the assertion pass") {
          runReported(test("fails")(!ints.assertNoDiff(1, 2) && assertTrue(1 == 2))).map { run =>
            assertTrue(run.results.map(_.isLeft) == Chunk(true), run.reports.isEmpty)
          }
        },
        test("reports a diff under two negations") {
          // noinspection DoubleNegationScala
          runReported(test("fails")(!(!ints.assertNoDiff(1, 2)))).map { run =>
            assertTrue(run.results.map(_.isLeft) == Chunk(true), run.reportedDiffs == Chunk(ints.diff(1, 2)))
          }
        },
        test("does not report a diff in an || that passed") {
          val tests = test("fails")((ints.assertNoDiff(1, 2) || assertTrue(1 == 1)) && assertTrue(1 == 2))
          runReported(tests).map { run =>
            assertTrue(run.results.map(_.isLeft) == Chunk(true), run.reports.isEmpty)
          }
        },
        test("reports a diff that makes <==> fail") {
          runReported(test("fails")(ints.assertNoDiff(1, 2) <==> assertTrue(1 == 1))).map { run =>
            assertTrue(run.results.map(_.isLeft) == Chunk(true), run.reportedDiffs == Chunk(ints.diff(1, 2)))
          }
        },
        test("does not report a diff in a <==> that passed") {
          val tests = test("fails")((ints.assertNoDiff(1, 2) <==> assertTrue(1 == 2)) && assertTrue(1 == 2))
          runReported(tests).map { run =>
            assertTrue(run.results.map(_.isLeft) == Chunk(true), run.reports.isEmpty)
          }
        },
        test("reports the shrunk diff from a property test") {
          val property = test("fails") {
            check(Gen.int(0, 1000)) { i =>
              ints.assertNoDiff(if (i > 10) i + 1 else i, i)
            }
          }
          runReported(property).map { run =>
            assertTrue(run.reportedDiffs == Chunk(ints.diff(12, 11)))
          }
        },
        test("does not report passing tests or failures without a diff") {
          val tests = suite("suite")(
            test("passes")(ints.assertNoDiff(1, 1)),
            test("fails an assertion")(assertTrue(1 == 2)),
            test("dies")(ZIO.die(new RuntimeException("boom")).as(assertCompletes)),
            test("fails")(ZIO.fail("boom").as(assertCompletes)),
          )
          runReported(tests).map { run =>
            assertTrue(run.results.count(_.isLeft) == 3, run.reports.isEmpty)
          }
        },
      ),
      suite("diffliciousReporting")(
        // Being in shared code, this also checks that every platform provides it. On the JVM a report for a failed diff
        // would land in the real output directory, so the scripted test covers that instead.
        test("can be applied like any other aspect") {
          ZIO.scoped[TestEnvironment](runTests(test("passes")(ints.assertNoDiff(1, 1)) @@ diffliciousReporting)).map {
            results => assertTrue(results.map(_.isRight) == Chunk(true))
          }
        },
      ),
      suite("withoutSpecName")(
        test("drops a root suite named after the spec") {
          val hierarchy = ZIODiffliciousSpecBase.withoutSpecName("MySpec", Vector("MySpec", "inner", "fails"))
          assertTrue(hierarchy == Vector("inner", "fails"))
        },
        test("keeps a root suite with any other name") {
          val hierarchy = ZIODiffliciousSpecBase.withoutSpecName("MySpec", Vector("outer", "inner", "fails"))
          assertTrue(hierarchy == Vector("outer", "inner", "fails"))
        },
        test("keeps a lone label named after the spec") {
          assertTrue(ZIODiffliciousSpecBase.withoutSpecName("MySpec", Vector("MySpec")) == Vector("MySpec"))
        },
      ),
    )

  private type Results = Chunk[Either[TestFailure[Any], TestSuccess]]

  private final case class Run(results: Results, reports: Chunk[(Vector[String], DifferenceFound)]) {
    def reportedDiffs: Chunk[DiffResult] = reports.map(_._2.diffResult)
  }

  // Runs every test in `spec` with the reporting aspect applied, returning each test's outcome and what was reported
  private def runReported(spec: Spec[TestEnvironment, Any]): ZIO[TestEnvironment, Nothing, Run] =
    for {
      reported <- Ref.make(Chunk.empty[(Vector[String], DifferenceFound)])
      aspect = ZIODiffliciousSpecBase.reportingAspect((path, found) => reported.update(_ :+ (path -> found)))
      results <- ZIO.scoped[TestEnvironment](runTests(spec @@ aspect))
      reports <- reported.get
    } yield Run(results, reports)

  private def runTests(spec: Spec[TestEnvironment, Any]): ZIO[TestEnvironment & Scope, Nothing, Results] =
    spec.foldScoped[TestEnvironment, Nothing, Results](ExecutionStrategy.Sequential) {
      case Spec.ExecCase(_, results) => ZIO.succeed(results)
      case Spec.LabeledCase(_, results) => ZIO.succeed(results)
      case Spec.ScopedCase(scoped) => scoped.fold[Results](failure => Chunk.single(Left(failure)), identity)
      case Spec.MultipleCase(results) => ZIO.succeed(results.flatten)
      case Spec.TestCase(test, _) => test.either.map(Chunk.single)
    }

  private def trackingDiffer(canUseEqualsValue: Boolean)(onDiff: => DiffResult): Differ[Int] =
    new Differ[Int] {
      override type R = DiffResult

      override val canUseEquals: Boolean = canUseEqualsValue

      override def diff(inputs: DiffInput[Int]): DiffResult = onDiff

      override protected def configureIgnored(newIgnored: Boolean): Differ[Int] = this

      override protected def configurePath(
        step: String,
        nextPath: ConfigurePath,
        op: ConfigureOp,
      ): Either[ConfigureError, Differ[Int]] = Left(ConfigureError.PathTooLong(nextPath))

      override protected def configurePairBy(
        path: ConfigurePath,
        op: ConfigureOp.PairBy[?],
      ): Either[ConfigureError, Differ[Int]] = Left(ConfigureError.InvalidConfigureOp(path, op, "TrackingDiffer"))
    }
}

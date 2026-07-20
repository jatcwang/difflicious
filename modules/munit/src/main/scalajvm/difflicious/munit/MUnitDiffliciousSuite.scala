package difflicious.munit

import difflicious.Differ
import difflicious.reporter.{DifferenceFound, DifferenceFoundException, DiffResultJsonlWriter}
import munit.{Location, Suite}

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

trait MUnitDiffliciousSuite extends Suite {
  implicit class DifferExtensions[A](differ: Differ[A]) {
    def assertNoDiff(obtained: A, expected: A)(implicit loc: Location): Unit = {
      val result = differ.diff(obtained, expected)
      if (!result.isOk)
        throw DifferenceFoundException(
          diffResult = result,
          fileName = loc.filename,
          filePath = loc.path,
          lineNumber = loc.line,
        )
    }
  }

  private lazy val diffliciousJsonlWriter = new DiffResultJsonlWriter()

  abstract override def munitTests(): Seq[Test] =
    super.munitTests().map { test =>
      test.withBody { () =>
        implicit val executionContext = munitExecutionContext
        val result =
          try test.body()
          catch {
            case NonFatal(error) => Future.failed(error)
          }

        result.transform { outcome =>
          reportDiffFailure(test, outcome)
          outcome
        }
      }
    }

  private def reportDiffFailure(test: Test, outcome: Try[Any]): Unit =
    outcome match {
      case Failure(error) =>
        DifferenceFound.fromThrowable(error).foreach { failure =>
          val className = getClass.getName.stripSuffix("$")
          val suiteName = className.split('.').lastOption.getOrElse(className)
          diffliciousJsonlWriter.write(
            suiteName = suiteName,
            suiteId = className,
            suiteClassName = Some(className),
            testName = test.name,
            testText = test.name,
            testHierarchy = Vector(test.name),
          )(failure)
        }
      case _ =>
    }
}

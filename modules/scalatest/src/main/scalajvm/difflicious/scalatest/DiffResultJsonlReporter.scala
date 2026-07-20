package difflicious.scalatest

import difflicious.reporter.{DifferenceFound, DiffResultJsonlWriter}
import org.scalatest.Reporter
import org.scalatest.events.{Event, ScopeClosed, ScopeOpened, SuiteAborted, SuiteCompleted, TestFailed}

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

final class DiffResultJsonlReporter private[scalatest] (writer: DiffResultJsonlWriter) extends Reporter {
  // Called reflectively by ScalaTest's ReporterFactory.
  def this() = this(new DiffResultJsonlWriter())

  private val suiteScopes: concurrent.Map[String, Vector[String]] = TrieMap.empty

  override def apply(event: Event): Unit =
    event match {
      case scopeOpened: ScopeOpened =>
        updateSuiteScopes(scopeOpened.nameInfo.suiteId)(_ :+ scopeOpened.message)

      case scopeClosed: ScopeClosed =>
        updateSuiteScopes(scopeClosed.nameInfo.suiteId)(DiffResultJsonlReporter.popScope(_, scopeClosed.message))

      case testFailed: TestFailed =>
        testFailed.throwable.flatMap(DifferenceFound.fromThrowable).foreach { failure =>
          writer.write(
            suiteName = testFailed.suiteName,
            suiteId = testFailed.suiteId,
            suiteClassName = testFailed.suiteClassName,
            testName = testFailed.testName,
            testText = testFailed.testText,
            testHierarchy = hierarchyFor(testFailed),
          )(failure)
        }

      case suiteCompleted: SuiteCompleted =>
        removeSuiteScopes(suiteCompleted.suiteId)

      case suiteAborted: SuiteAborted =>
        removeSuiteScopes(suiteAborted.suiteId)

      case _ =>
    }

  private def hierarchyFor(event: TestFailed): Vector[String] = {
    val scopes = suiteScopes.getOrElse(event.suiteId, Vector.empty)
    if (scopes.nonEmpty) scopes :+ event.testText else Vector(event.testName)
  }

  private def updateSuiteScopes(suiteId: String)(update: Vector[String] => Vector[String]): Unit = {
    val _ = suiteScopes.updateWith(suiteId) { currentScopes =>
      Some(update(currentScopes.getOrElse(Vector.empty)))
    }
    ()
  }

  private def removeSuiteScopes(suiteId: String): Unit = {
    val _ = suiteScopes.remove(suiteId)
    ()
  }
}

object DiffResultJsonlReporter {
  private[scalatest] def popScope(scopes: Vector[String], scope: String): Vector[String] =
    scopes.lastIndexOf(scope) match {
      case -1 => scopes
      case index => scopes.take(index)
    }
}

package difflicious.reporter

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString}
import difflicious.DiffResult

/** Information regarding a diff test failure, such as the name of the test suite and test. The data in this case class
  * are serialized by the reporter
  */
final case class DiffResultTestDetails(
  runId: String,
  testId: String,
  suiteName: String,
  suiteId: String,
  suiteClassName: Option[String],
  testName: String,
  testText: String,
  testHierarchy: Vector[String],
  fileName: String,
  filePath: String,
  lineNumber: Int,
  diffResult: DiffResult,
)

object DiffResultTestDetails {
  def fromJsonString(json: String): DiffResultTestDetails =
    readFromString(json)

  implicit lazy val jsonValueCodec: JsonValueCodec[DiffResultTestDetails] =
    DiffResultTestDetailsJson.jsonValueCodec
}

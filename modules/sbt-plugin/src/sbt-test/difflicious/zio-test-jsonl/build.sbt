import difflicious.sbt.DiffliciousPlugin.autoImport.*
import sbtcompat.PluginCompat.*

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / diffliciousCliAutoDependency := false

libraryDependencies ++= Seq(
  "com.github.jatcwang" %% "difflicious-zio-test" % sys.props("plugin.version"),
  "dev.zio" %% "zio-test-sbt" % "2.1.26",
).map(_ % Test)

Test / diffliciousScalaTestJsonlReporterEnabled := false

lazy val checkZIOTestJsonlReport = taskKey[Unit]("Verify the ZIO Test integration writes suite and test metadata")

checkZIOTestJsonlReport := Def.uncached {
  val reportFiles = ((Test / target).value / "difflicious-report" ** "*.jsonl").get()
  assert(reportFiles.size == 2, s"expected one JSONL report per spec, got $reportFiles")

  def recordsOf(spec: String): List[String] = {
    val fileName = s"example.$spec.jsonl"
    IO.readLines(reportFiles.find(_.getName == fileName).getOrElse(sys.error(s"missing $fileName in $reportFiles")))
  }

  val lines = recordsOf("ZIOTestJsonlSpec")
  assert(lines.size == 2, s"expected two JSONL records, got $lines")

  def assertField(json: String, field: String, value: String): Unit =
    assert(json.contains("\"" + field + "\":\"" + value + "\""), s"expected $field=$value in $json")

  val specLines = IO.readLines(baseDirectory.value / "src/test/scala/example/ZIOTestJsonlSpec.scala")
  def lineOf(code: String): Int = specLines.indexWhere(_.contains(code)) + 1

  Seq(
    "reports diff result" -> lineOf("ints.assertNoDiff(1, 2)"),
    "reports effectful diff result" -> lineOf("ints.assertNoDiff(2, 3)"),
  ).foreach { case (testName, lineNumber) =>
    val json = lines.find(_.contains("\"testName\":\"" + testName + "\"")).getOrElse {
      sys.error(s"missing JSONL record for $testName in $lines")
    }
    assertField(json, "suiteName", "ZIOTestJsonlSpec")
    assertField(json, "suiteId", "example.ZIOTestJsonlSpec")
    assertField(json, "suiteClassName", "example.ZIOTestJsonlSpec")
    assertField(json, "testName", testName)
    assertField(json, "testText", testName)
    assertField(json, "fileName", "ZIOTestJsonlSpec.scala")
    assert(
      json.contains("src/test/scala/example/ZIOTestJsonlSpec.scala\",\"lineNumber\":" + lineNumber + ","),
      s"expected filePath and lineNumber=$lineNumber in $json",
    )
    assert(
      json.contains("\"testHierarchy\":[\"inner\",\"" + testName + "\"]"),
      s"expected test hierarchy without the root suite named after the spec in $json",
    )
    assert(json.contains("\"diffResult\":"), s"expected diff result in $json")
  }

  val replacedAspectsLines = recordsOf("ZIOTestReplacedAspectsSpec")
  assert(replacedAspectsLines.size == 1, s"expected one JSONL record, got $replacedAspectsLines")
  assertField(replacedAspectsLines.head, "testName", "reports with replaced aspects")
}

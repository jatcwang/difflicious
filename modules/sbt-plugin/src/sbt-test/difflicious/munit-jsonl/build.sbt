import difflicious.sbt.DiffliciousPlugin.autoImport.*
import sbtcompat.PluginCompat.*

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / diffliciousCliAutoDependency := false

libraryDependencies ++= Seq(
  "com.github.jatcwang" %% "difflicious-munit" % sys.props("plugin.version"),
  "org.scalameta" %% "munit" % "1.3.4",
).map(_ % Test)

Test / diffliciousScalaTestJsonlReporterEnabled := false

lazy val checkMUnitJsonlReport = taskKey[Unit]("Verify the MUnit integration writes suite and test metadata")

checkMUnitJsonlReport := Def.uncached {
  val reportFiles = ((Test / target).value / "difflicious-report" ** "*.jsonl").get()
  assert(reportFiles.size == 1, s"expected one JSONL report, got $reportFiles")

  val lines = IO.readLines(reportFiles.head)
  assert(lines.size == 1, s"expected one JSONL record, got $lines")
  val json = lines.head

  def assertField(field: String, value: String): Unit =
    assert(json.contains("\"" + field + "\":\"" + value + "\""), s"expected $field=$value in $json")

  assertField("suiteName", "MUnitJsonlSuite")
  assertField("suiteId", "example.MUnitJsonlSuite")
  assertField("suiteClassName", "example.MUnitJsonlSuite")
  assertField("testName", "reports diff result")
  assertField("testText", "reports diff result")
  assert(
    json.contains("\"testHierarchy\":[\"reports diff result\"]"),
    s"expected test hierarchy in $json",
  )
  assert(json.contains("\"diffResult\":"), s"expected diff result in $json")
}

import difflicious.sbt.DiffliciousPlugin.autoImport.*
import sbtcompat.PluginCompat.*

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / diffliciousCliAutoDependency := false

libraryDependencies ++= Seq(
  "com.github.jatcwang" %% "difflicious-weaver" % sys.props("plugin.version"),
  "org.typelevel" %% "weaver-cats" % "0.13.0",
).map(_ % Test)

Test / testFrameworks += new TestFramework("weaver.framework.CatsEffect")
Test / diffliciousScalaTestJsonlReporterEnabled := false

lazy val checkWeaverJsonlReport = taskKey[Unit]("Verify the Weaver integration writes suite and test metadata")

checkWeaverJsonlReport := Def.uncached {
  val reportFiles = ((Test / target).value / "difflicious-report" ** "*.jsonl").get()
  assert(reportFiles.size == 1, s"expected one JSONL report, got $reportFiles")

  val lines = IO.readLines(reportFiles.head)
  assert(lines.size == 2, s"expected two JSONL records, got $lines")

  def assertField(json: String, field: String, value: String): Unit =
    assert(json.contains("\"" + field + "\":\"" + value + "\""), s"expected $field=$value in $json")

  Seq("reports diff result", "reports effectful diff result").foreach { testName =>
    val json = lines.find(_.contains("\"testName\":\"" + testName + "\"")).getOrElse {
      sys.error(s"missing JSONL record for $testName in $lines")
    }
    assertField(json, "suiteName", "example.WeaverJsonlSuite")
    assertField(json, "suiteId", "example.WeaverJsonlSuite")
    assertField(json, "suiteClassName", "example.WeaverJsonlSuite")
    assertField(json, "testName", testName)
    assertField(json, "testText", testName)
    assertField(json, "fileName", "WeaverJsonlSuite.scala")
    assertField(json, "filePath", "WeaverJsonlSuite.scala")
    assert(
      json.contains("\"testHierarchy\":[\"" + testName + "\"]"),
      s"expected test hierarchy in $json",
    )
    assert(json.contains("\"diffResult\":"), s"expected diff result in $json")
  }
}

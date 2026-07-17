package difflicious.reporter

object DiffliciousResultDefaults {
  val OutputDirectory = "target/difflicious-result"
  val OutputDirectoryProperty = "difflicious.report.outputDir"
  val ZeroUlid = Ulid.Zero
  val RunIdProperty = "difflicious.runId"
}

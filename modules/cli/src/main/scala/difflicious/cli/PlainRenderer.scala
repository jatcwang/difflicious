package difflicious.cli

import difflicious.DiffResult

object PlainRenderer {
  def render(report: DiffReport): String =
    report.runs match {
      case Vector() =>
        "Difflicious diff report: ok\nSummary: no diff failures found.\n"

      case Vector(run) if run.metadata.isEmpty =>
        render(run.result, run.changes)

      case runs =>
        val builder = new StringBuilder
        builder.append("Difflicious diff report: ")
        builder.append(if (report.isOk) "ok" else "different")
        builder.append('\n')
        builder.append("Summary: ")
        builder.append(runs.length)
        builder.append(" diff failure(s), ")
        builder.append(report.totalChanges)
        builder.append(" non-ignored change(s).\n")

        runs.zipWithIndex.foreach { case (run, index) =>
          builder.append('\n')
          builder.append("Failure ")
          builder.append(index + 1)
          builder.append(": ")
          builder.append(run.metadata.fold("Raw comparison")(_.displayName))
          builder.append('\n')
          run.metadata.foreach { metadata =>
            builder.append("Run id: ")
            builder.append(metadata.runId)
            builder.append('\n')
            builder.append("Test id: ")
            builder.append(metadata.testId)
            builder.append('\n')
            builder.append("Location: ")
            builder.append(metadata.location)
            builder.append('\n')
          }
          builder.append(render(run.result, run.changes))
        }

        builder.result()
    }

  def render(result: DiffResult, changes: Vector[DiffChange]): String = {
    val summary = DiffSummary.fromResult(result.isOk, changes)
    val builder = new StringBuilder

    builder.append("Difflicious diff result: ")
    builder.append(if (result.isOk) "ok" else "different")
    builder.append('\n')
    builder.append("Summary: ")
    builder.append(summary.totalChanges)
    builder.append(" non-ignored change(s)")
    if (summary.ignored > 0) {
      builder.append(", ")
      builder.append(summary.ignored)
      builder.append(" ignored subtree(s)")
    }
    builder.append(".\n")

    if (changes.nonEmpty) {
      builder.append('\n')
      builder.append("Differences:\n")
      changes.zipWithIndex.foreach { case (change, index) =>
        if (index > 0) builder.append('\n')
        appendChange(builder, index + 1, change)
      }
    }

    builder.result()
  }

  private def appendChange(builder: StringBuilder, index: Int, change: DiffChange): Unit = {
    builder.append(index)
    builder.append(". ")
    builder.append(change.path.render)
    builder.append(" - ")
    builder.append(change.kind.name)
    builder.append(" (")
    builder.append(change.typeName)
    builder.append(")")
    builder.append('\n')

    change.obtained.foreach { obtained =>
      if (change.kind == ChangeKind.ObtainedOnly)
        appendStructuredValue(builder, "obtained", obtained)
      else {
        builder.append("   obtained: ")
        builder.append(oneLine(obtained))
        builder.append('\n')
      }
    }

    change.expected.foreach { expected =>
      builder.append("   expected: ")
      builder.append(oneLine(expected))
      builder.append('\n')
    }
  }

  private def appendStructuredValue(builder: StringBuilder, label: String, value: String): Unit = {
    builder.append("   ")
    builder.append(label)
    builder.append(":\n")
    Ansi.strip(value).linesIterator.foreach { line =>
      builder.append("      ")
      builder.append(line)
      builder.append('\n')
    }
  }

  private def oneLine(value: String): String = {
    val normalized = Ansi.strip(value).linesIterator.map(_.trim).filter(_.nonEmpty).mkString(" ")
    if (normalized.length <= 240) normalized else normalized.take(237) + "..."
  }
}

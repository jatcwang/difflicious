package difflicious.cli

import difflicious.DiffResult
import difflicious.DiffResult.MapResult.Entry
import difflicious.DiffResult.ValueResult
import difflicious.utils.TypeName
import io.circe.Json

object JsonRenderer {
  def renderReport(report: DiffReport): Json =
    obj(
      "isOk" -> Json.fromBoolean(report.isOk),
      "summary" -> obj(
        "failures" -> Json.fromInt(report.runs.length),
        "totalChanges" -> Json.fromInt(report.totalChanges),
      ),
      "failures" -> Json.fromValues(report.runs.map(runJson)),
    )

  def renderReportString(report: DiffReport): String =
    renderReport(report).spaces2

  def render(result: DiffResult, changes: Vector[DiffChange]): Json = {
    val summary = DiffSummary.fromResult(result.isOk, changes)
    obj(
      "isOk" -> Json.fromBoolean(result.isOk),
      "summary" -> summaryJson(summary),
      "changes" -> Json.fromValues(changes.map(changeJson)),
      "tree" -> resultJson(result),
    )
  }

  def renderString(result: DiffResult, changes: Vector[DiffChange]): String =
    render(result, changes).spaces2

  private def resultJson(result: DiffResult): Json =
    result match {
      case result: DiffResult.ListResult =>
        withCommon(
          kind = "list",
          result = result,
          typeName = Some(result.typeName),
          extra = Vector(
            "items" -> Json.fromValues(result.items.map(resultJson)),
          ),
        )

      case result: DiffResult.RecordResult =>
        withCommon(
          kind = "record",
          result = result,
          typeName = Some(result.typeName),
          extra = Vector(
            "fields" -> Json.fromValues(
              result.fields.toVector.map { case (fieldName, value) =>
                obj(
                  "name" -> Json.fromString(fieldName),
                  "value" -> resultJson(value),
                )
              },
            ),
          ),
        )

      case result: DiffResult.MapResult =>
        withCommon(
          kind = "map",
          result = result,
          typeName = Some(result.typeName),
          extra = Vector(
            "entries" -> Json.fromValues(
              result.entries.map { case Entry(key, value) =>
                obj(
                  "key" -> Json.fromString(key),
                  "value" -> resultJson(value),
                )
              },
            ),
          ),
        )

      case result: DiffResult.MismatchTypeResult =>
        withCommon(
          kind = "type_mismatch",
          result = result,
          typeName = None,
          extra = Vector(
            "obtainedTypeName" -> typeNameJson(result.obtainedTypeName),
            "expectedTypeName" -> typeNameJson(result.expectedTypeName),
            "obtained" -> resultJson(result.obtained),
            "expected" -> resultJson(result.expected),
          ),
        )

      case result: DiffResult.ValueResult =>
        valueJson(result)
    }

  private def valueJson(result: DiffResult.ValueResult): Json =
    result match {
      case result: ValueResult.Both =>
        withCommon(
          kind = "value",
          result = result,
          typeName = None,
          extra = Vector(
            "obtained" -> Json.fromString(result.obtained),
            "expected" -> Json.fromString(result.expected),
            "isSame" -> Json.fromBoolean(result.isSame),
          ),
        )

      case result: ValueResult.ObtainedOnly =>
        withCommon(
          kind = "value",
          result = result,
          typeName = None,
          extra = Vector(
            "obtained" -> Json.fromString(result.obtained),
          ),
        )

      case result: ValueResult.ExpectedOnly =>
        withCommon(
          kind = "value",
          result = result,
          typeName = None,
          extra = Vector(
            "expected" -> Json.fromString(result.expected),
          ),
        )
    }

  private def withCommon(
    kind: String,
    result: DiffResult,
    typeName: Option[TypeName.SomeTypeName],
    extra: Vector[(String, Json)],
  ): Json =
    Json.fromFields(
      Vector(
        "kind" -> Json.fromString(kind),
        "pairType" -> Json.fromString(DiffResultInspector.pairTypeName(result.pairType)),
        "isIgnored" -> Json.fromBoolean(result.isIgnored),
        "isOk" -> Json.fromBoolean(result.isOk),
        "typeName" -> typeName.fold(Json.Null)(typeNameJson),
      ) ++ extra,
    )

  private def changeJson(change: DiffChange): Json =
    obj(
      "path" -> Json.fromString(change.path.render),
      "kind" -> Json.fromString(change.kind.name),
      "pairType" -> Json.fromString(DiffResultInspector.pairTypeName(change.pairType)),
      "typeName" -> change.typeName.fold(Json.Null)(Json.fromString),
      "obtained" -> change.obtained.fold(Json.Null)(Json.fromString),
      "expected" -> change.expected.fold(Json.Null)(Json.fromString),
      "isIgnored" -> Json.fromBoolean(change.isIgnored),
      "isOk" -> Json.fromBoolean(change.isOk),
      "rendered" -> Json.fromString(change.rendered),
    )

  private def summaryJson(summary: DiffSummary): Json =
    obj(
      "isOk" -> Json.fromBoolean(summary.isOk),
      "totalChanges" -> Json.fromInt(summary.totalChanges),
      "changed" -> Json.fromInt(summary.changed),
      "typeMismatches" -> Json.fromInt(summary.typeMismatches),
      "obtainedOnly" -> Json.fromInt(summary.obtainedOnly),
      "expectedOnly" -> Json.fromInt(summary.expectedOnly),
      "ignored" -> Json.fromInt(summary.ignored),
    )

  private def typeNameJson(typeName: TypeName.SomeTypeName): Json =
    obj(
      "short" -> Json.fromString(typeName.short),
      "long" -> Json.fromString(typeName.long),
      "withTypeParamsLong" -> Json.fromString(typeName.withTypeParamsLong),
    )

  private def obj(fields: (String, Json)*): Json =
    Json.fromFields(fields)

  private def runJson(run: DiffRun): Json = {
    val summary = DiffSummary.fromResult(run.result.isOk, run.changes)
    Json.fromFields(
      Vector(
        "metadata" -> run.metadata.fold(Json.Null)(metadataJson),
        "isOk" -> Json.fromBoolean(run.result.isOk),
        "summary" -> summaryJson(summary),
        "changes" -> Json.fromValues(run.changes.map(changeJson)),
        "tree" -> resultJson(run.result),
      ),
    )
  }

  private def metadataJson(metadata: DiffRunMetadata): Json =
    obj(
      "runId" -> Json.fromString(metadata.runId),
      "testId" -> Json.fromString(metadata.testId),
      "suiteName" -> Json.fromString(metadata.suiteName),
      "suiteId" -> Json.fromString(metadata.suiteId),
      "suiteClassName" -> metadata.suiteClassName.fold(Json.Null)(Json.fromString),
      "testName" -> Json.fromString(metadata.testName),
      "testText" -> Json.fromString(metadata.testText),
      "testHierarchy" -> Json.fromValues(metadata.testHierarchy.map(Json.fromString)),
      "fileName" -> Json.fromString(metadata.fileName),
      "filePath" -> Json.fromString(metadata.filePath),
      "lineNumber" -> Json.fromInt(metadata.lineNumber),
    )
}

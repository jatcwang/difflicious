package difflicious.cli

import difflicious.DiffResult
import difflicious.DiffResult.MapResult.Entry
import difflicious.DiffResult.ValueResult
import difflicious.DiffResultPrinter
import difflicious.PairType
import difflicious.utils.TypeName
import io.circe.parser.decode

import scala.collection.immutable.ListMap

object DiffResultInspector {
  def collectChanges(result: DiffResult): Vector[DiffChange] =
    collect(result, DiffPath.root)

  def pairTypeName(pairType: PairType): String =
    pairType match {
      case PairType.Both => "both"
      case PairType.ObtainedOnly => "obtained_only"
      case PairType.ExpectedOnly => "expected_only"
    }

  def typeNameShort(typeName: TypeName.SomeTypeName): String =
    typeName.short

  def plainRender(result: DiffResult): String =
    Ansi.strip(DiffResultPrinter.consoleOutput(result, 0).render)

  private def collect(result: DiffResult, path: DiffPath): Vector[DiffChange] =
    if (result.isIgnored) Vector(ignoredChange(result, path))
    else
      result match {
        case result: DiffResult.ListResult =>
          if (result.pairType == PairType.Both)
            result.items.zipWithIndex.flatMap { case (item, index) =>
              collect(item, path / DiffPath.Index(index))
            }
          else Vector(subtreeChange(result, path, typeName = Some(result.typeName.short)))

        case result: DiffResult.RecordResult =>
          if (result.pairType == PairType.Both)
            orderedFields(result.fields).flatMap { case (fieldName, value) =>
              collect(value, path / DiffPath.Field(fieldName))
            }
          else Vector(subtreeChange(result, path, typeName = Some(result.typeName.short)))

        case result: DiffResult.MapResult =>
          if (result.pairType == PairType.Both)
            result.entries.flatMap { case Entry(key, value) =>
              collect(value, path / DiffPath.Field(decodeMapKey(key)))
            }
          else Vector(subtreeChange(result, path, typeName = Some(result.typeName.short)))

        case result: DiffResult.MismatchTypeResult =>
          Vector(
            DiffChange(
              path = path,
              kind = ChangeKind.TypeMismatch,
              pairType = result.pairType,
              typeName = Some(s"${result.obtainedTypeName.short} != ${result.expectedTypeName.short}"),
              obtained = Some(plainRender(result.obtained)),
              expected = Some(plainRender(result.expected)),
              isIgnored = result.isIgnored,
              isOk = result.isOk,
              rendered = plainRender(result),
            ),
          )

        case result: DiffResult.ValueResult =>
          collectValue(result, path)
      }

  private def collectValue(result: DiffResult.ValueResult, path: DiffPath): Vector[DiffChange] =
    result match {
      case value: ValueResult.Both if value.isSame =>
        Vector.empty

      case value: ValueResult.Both =>
        Vector(
          DiffChange(
            path = path,
            kind = ChangeKind.Changed,
            pairType = value.pairType,
            typeName = None,
            obtained = Some(value.obtained),
            expected = Some(value.expected),
            isIgnored = value.isIgnored,
            isOk = value.isOk,
            rendered = plainRender(value),
          ),
        )

      case value: ValueResult.ObtainedOnly =>
        Vector(
          DiffChange(
            path = path,
            kind = ChangeKind.ObtainedOnly,
            pairType = value.pairType,
            typeName = None,
            obtained = Some(value.obtained),
            expected = None,
            isIgnored = value.isIgnored,
            isOk = value.isOk,
            rendered = plainRender(value),
          ),
        )

      case value: ValueResult.ExpectedOnly =>
        Vector(
          DiffChange(
            path = path,
            kind = ChangeKind.ExpectedOnly,
            pairType = value.pairType,
            typeName = None,
            obtained = None,
            expected = Some(value.expected),
            isIgnored = value.isIgnored,
            isOk = value.isOk,
            rendered = plainRender(value),
          ),
        )
    }

  private def ignoredChange(result: DiffResult, path: DiffPath): DiffChange =
    DiffChange(
      path = path,
      kind = ChangeKind.Ignored,
      pairType = result.pairType,
      typeName = resultTypeName(result),
      obtained = None,
      expected = None,
      isIgnored = result.isIgnored,
      isOk = result.isOk,
      rendered = plainRender(result),
    )

  private def subtreeChange(result: DiffResult, path: DiffPath, typeName: Option[String]): DiffChange = {
    val kind =
      result.pairType match {
        case PairType.ObtainedOnly => ChangeKind.ObtainedOnly
        case PairType.ExpectedOnly => ChangeKind.ExpectedOnly
        case PairType.Both => ChangeKind.Changed
      }

    val rendered = plainRender(result)
    DiffChange(
      path = path,
      kind = kind,
      pairType = result.pairType,
      typeName = typeName,
      obtained = if (result.pairType != PairType.ExpectedOnly) Some(rendered) else None,
      expected = if (result.pairType != PairType.ObtainedOnly) Some(rendered) else None,
      isIgnored = result.isIgnored,
      isOk = result.isOk,
      rendered = rendered,
    )
  }

  private def resultTypeName(result: DiffResult): Option[String] =
    result match {
      case result: DiffResult.ListResult => Some(result.typeName.short)
      case result: DiffResult.RecordResult => Some(result.typeName.short)
      case result: DiffResult.MapResult => Some(result.typeName.short)
      case result: DiffResult.MismatchTypeResult =>
        Some(s"${result.obtainedTypeName.short} != ${result.expectedTypeName.short}")
      case _: DiffResult.ValueResult => None
    }

  private def orderedFields(fields: ListMap[String, DiffResult]): Vector[(String, DiffResult)] =
    fields.toVector

  private def decodeMapKey(key: String): String =
    decode[String](key).getOrElse(key)
}

package difflicious

import difflicious.DiffResult.MapResult.Entry
import difflicious.DiffResult.ValueResult
import difflicious.utils.TypeName
import fansi.{Str, Color}

object DiffResultPrinter {
  private val colorActual = Color.Red
  private val colorExpected = Color.Green
  private val colorIgnored = Color.DarkGray

  private val indentStep = 2

  private val ignoredStr: Str = Str("[IGNORED]").overlay(colorIgnored)

  def consolePrint(
    res: DiffResult,
  ): Unit = {
    println(
      consoleOutput(
        res,
        indentLevel = 0,
      ).render,
    )
  }

  def consoleOutput(
    res: DiffResult,
    indentLevel: Int,
  ): fansi.Str = {
    if (res.isIgnored) ignoredStr
    else
      res match {
        case r: DiffResult.ListResult =>
          listResultToStr(
            typeName = r.typeName,
            diffResults = r.items,
            indentLevel = indentLevel,
            matchType = r.matchType,
          )
        case r: DiffResult.SetResult =>
          listResultToStr(
            typeName = r.typeName,
            diffResults = r.items,
            indentLevel = indentLevel,
            matchType = r.matchType,
          )
        case r: DiffResult.RecordResult => {
          val indentForFields = Str("\n" ++ indentLevel.asSpacesPlus1)
          val fieldsStr = r.fields
            .map {
              case (fieldName, vRes) =>
                Str(fieldName) ++ ": " ++ consoleOutput(
                  vRes,
                  indentLevel = indentLevel + 1,
                ) ++ ","
            }
            .foldLeft(Str("")) { case (accum, nextStr) => accum ++ indentForFields ++ nextStr }
          val allStr = Str(s"${r.typeName.short}(") ++ fieldsStr ++ s"\n${indentLevel.asSpaces})"
          colorOnMatchType(str = allStr, matchType = r.matchType)
        }
        case r: DiffResult.MapResult => {
          val indentPlusStr = s"\n${indentLevel.asSpacesPlus1}"
          val keyValStr = r.entries
            .map {
              case Entry(keyJson, valueDiff) => {
                val keyStr =
                  colorOnMatchType(str = Str(keyJson.noSpaces), matchType = valueDiff.matchType)
                val valueStr = consoleOutput(valueDiff, indentLevel + 2)
                keyStr ++ " -> " ++ valueStr ++ ","
              }
            }
            .foldLeft(Str("")) {
              case (accum, nextStr) =>
                accum ++ indentPlusStr ++ nextStr
            }
          val allStr = Str(r.typeName.short ++ "(") ++ keyValStr ++ s"\n${indentLevel.asSpaces})"
          colorOnMatchType(allStr, r.matchType)
        }
        case r: DiffResult.MismatchTypeResult => {
          val titleStr = Str(r.actualTypeName.short).overlay(colorActual) ++ " != " ++ Str(r.expectedTypeName.short)
            .overlay(colorExpected)
          val allStr = if (r.isIgnored) {
            titleStr
          } else {
            val actualStr = consoleOutput(r.actual, indentLevel)
            val expectedStr = consoleOutput(r.expected, indentLevel)
            val indentSplitStr = Str(s"\n${indentLevel.asSpaces}")
            titleStr ++
              indentSplitStr ++ (Str("=== Actual ===") ++ indentSplitStr ++ actualStr).overlay(colorActual) ++
              indentSplitStr ++ (Str("=== Expected ===") ++ indentSplitStr ++ expectedStr).overlay(colorExpected)
          }
          colorOnMatchType(str = allStr, matchType = r.matchType)
        }
        case result: DiffResult.ValueResult =>
          result match {
            case r: ValueResult.Both => {
              val actualStr = Str(r.actual.noSpaces)
              if (r.isSame) {
                actualStr
              } else {
                val expectedStr = Str(r.expected.noSpaces)
                actualStr.overlay(colorActual) ++ " -> " ++ expectedStr.overlay(colorExpected)
              }
            }
            case ValueResult.ActualOnly(actual, _)     => fansi.Str(actual.noSpaces)
            case ValueResult.ExpectedOnly(expected, _) => fansi.Str(expected.noSpaces)
          }
      }
  }

  private def listResultToStr(
    typeName: TypeName,
    diffResults: Seq[DiffResult],
    indentLevel: Int,
    matchType: MatchType,
  ) = {
    val indentForFields = Str("\n" ++ indentLevel.asSpacesPlus1)
    val listStrs = diffResults
      .map { res =>
        consoleOutput(res, indentLevel + 1) ++ ","
      }
      .foldLeft(Str("")) { case (accum, next) => accum ++ indentForFields ++ next }
    val allStr = Str(s"${typeName.short}(") ++ listStrs ++ Str(s"\n${indentLevel.asSpaces})")
    colorOnMatchType(str = allStr, matchType = matchType)
  }

  private def colorOnMatchType(
    str: Str,
    matchType: MatchType,
  ): Str = {
    matchType match {
      case MatchType.Both         => str
      case MatchType.ActualOnly   => str.overlay(colorActual)
      case MatchType.ExpectedOnly => str.overlay(colorExpected)
    }
  }

  implicit private class IntExt(val i: Int) extends AnyVal {
    @inline
    def asSpaces: String = " " * (i * indentStep)

    @inline
    def asSpacesPlus1: String = " " * ((i + 1) * indentStep)
  }

}

package difflicious

import difflicious.DiffResult.ValueResult
import fansi.{Color, Str}

object DiffResultPrinter {
  private val colorActual = Color.Red
  private val colorExpected = Color.Green
  private val colorIgnored = Color.DarkGray

  private val indentStep = 2

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

  private def colorOnMatchType(
    str: Str,
    isIgnored: Boolean,
    matchType: MatchType,
  ): Str = {
    if (isIgnored) str.overlay(colorIgnored)
    else
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

  def consoleOutput(
    res: DiffResult,
    indentLevel: Int,
  ): fansi.Str = {
    res match {
      case r: DiffResult.ListResult => {
        val indentForFields = Str("\n" ++ indentLevel.asSpacesPlus1)
        val listStrs = r.items
          .map { i =>
            consoleOutput(i, indentLevel + 1) ++ ","
          }
          .foldLeft(Str("")) { case (accum, next) => accum ++ indentForFields ++ next }
        val allStr = Str(s"${r.typeName.short}(") ++ listStrs ++ Str(s"\n${indentLevel.asSpaces})")
        colorOnMatchType(str = allStr, isIgnored = r.isIgnored, matchType = r.matchType)
      }
      case r: DiffResult.SetResult => ???
      case r: DiffResult.RecordResult => {
        val indentForFields = Str("\n" ++ indentLevel.asSpacesPlus1)
        val fieldsStr = r.fields
          .map {
            case (fieldName, valueResult) =>
              Str(fieldName) ++ ": " ++ consoleOutput(
                valueResult,
                indentLevel = indentLevel + 1,
              ) ++ ","
          }
          .foldLeft(Str("")) { case (accum, nextStr) => accum ++ indentForFields ++ nextStr }
        val allStr = Str(s"${r.typeName.short}(") ++ fieldsStr ++ s"\n${indentLevel.asSpaces})"
        colorOnMatchType(str = allStr, isIgnored = r.isIgnored, matchType = r.matchType)
      }
      case r: DiffResult.MapResult          => ???
      case r: DiffResult.MismatchTypeResult => ???
      case result: DiffResult.ValueResult =>
        result match {
          case ValueResult.Both(actual, expected, isOk, isIgnored) => {
            val actualStr = Str(actual.noSpaces)
            val expectedStr = Str(expected.noSpaces)
            if (isOk) {
              actualStr
            } else if (isIgnored) { // isOk is false
              (actualStr ++ " -> " ++ expectedStr).overlay(Color.DarkGray)
            } else {
              actualStr.overlay(colorActual) ++ " -> " ++ expectedStr.overlay(colorExpected)
            }
          }
          case ValueResult.ActualOnly(actual, _)     => fansi.Str(actual.noSpaces)
          case ValueResult.ExpectedOnly(expected, _) => fansi.Str(expected.noSpaces)
        }
    }
  }
}

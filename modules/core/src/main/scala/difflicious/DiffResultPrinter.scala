package difflicious

import difflicious.DiffResult.MapResult.Entry
import difflicious.DiffResult.ValueResult
import fansi.{Color, EscapeAttr, Str}

object DiffResultPrinter {
  private val colorObtained = Color.Red
  private val colorExpected = Color.Green
  private val colorIgnored = Color.DarkGray

  private val indentStep = 2

  private val ignoredStr: Str = Str("[IGNORED]").overlay(colorIgnored)

  private val emptyStr = Str("")

  def printDiffResult(
    res: DiffResult,
  ): Unit = {
    println(consoleOutput(res, 0).render)
  }

  def consoleOutput(
    res: DiffResult,
    indentLevel: Int,
  ): fansi.Str = {
    if (res.isIgnored) ignoredStr
    else
      res match {
        case r: DiffResult.ListResult => {
          val indentForFields = Str("\n" ++ indentLevel.asSpacesPlus1)
          val listStrs = r.items
            .map { res =>
              consoleOutput(res, indentLevel + 1)
            }
            .reduceLeftOption[Str] { case (accum, next) => accum ++ "," ++ indentForFields ++ next }
            .map(accum => indentForFields ++ accum)
            .getOrElse(emptyStr)
          val allStr = Str(s"${r.typeName.short}(") ++ listStrs ++ Str(s"\n${indentLevel.asSpaces})")
          colorOnMatchType(str = allStr, matchType = r.pairType)
        }
        case r: DiffResult.RecordResult => {
          val indentForFields = Str("\n" ++ indentLevel.asSpacesPlus1)
          val fieldsStr = r.fields
            .map { case (fieldName, vRes) =>
              Str(fieldName) ++ ": " ++ consoleOutput(
                vRes,
                indentLevel = indentLevel + 1,
              )
            }
            .reduceLeftOption[Str] { case (accum, nextStr) => accum ++ "," ++ indentForFields ++ nextStr }
            .map(accum => indentForFields ++ accum)
            .getOrElse(emptyStr)
          val allStr = Str(s"${r.typeName.short}(") ++ fieldsStr ++ s"\n${indentLevel.asSpaces})"
          colorOnMatchType(str = allStr, matchType = r.pairType)
        }
        case r: DiffResult.MapResult => {
          val indentPlusStr = Str(s"\n${indentLevel.asSpacesPlus1}")
          val keyValStr = r.entries
            .map { case Entry(keyJson, valueDiff) =>
              val keyStr =
                colorOnMatchType(str = Str(keyJson), matchType = valueDiff.pairType)
              val valueStr = consoleOutput(valueDiff, indentLevel + 2)
              keyStr ++ " -> " ++ valueStr
            }
            .reduceLeftOption[Str] { case (accum, nextStr) =>
              accum ++ "," ++ indentPlusStr ++ nextStr
            }
            .map(accum => indentPlusStr ++ accum)
            .getOrElse(emptyStr)
          val allStr = Str(r.typeName.short ++ "(") ++ keyValStr ++ s"\n${indentLevel.asSpaces})"
          colorOnMatchType(allStr, r.pairType)
        }
        case r: DiffResult.MismatchTypeResult => {
          val titleStr = Str(r.obtainedTypeName.short).overlay(colorObtained) ++ " != " ++ Str(r.expectedTypeName.short)
            .overlay(colorExpected)
          val allStr = {
            val obtainedStr = consoleOutput(r.obtained, indentLevel)
            val expectedStr = consoleOutput(r.expected, indentLevel)
            val indentSplitStr = Str(s"\n${indentLevel.asSpaces}")
            titleStr ++
              indentSplitStr ++ (Str("=== Obtained ===") ++ indentSplitStr ++ obtainedStr).overlay(colorObtained) ++
              indentSplitStr ++ (Str("=== Expected ===") ++ indentSplitStr ++ expectedStr).overlay(colorExpected)
          }
          colorOnMatchType(str = allStr, matchType = r.pairType)
        }
        case result: DiffResult.ValueResult =>
          result match {
            case r: ValueResult.Both => {
              val obtainedStr = Str(r.obtained)
              if (r.isSame) {
                obtainedStr
              } else {
                val expectedStr = Str(r.expected)
                obtainedStr.overlay(colorObtained) ++ " -> " ++ expectedStr.overlay(colorExpected)
              }
            }
            case ValueResult.ObtainedOnly(obtained, _) => fansi.Str(obtained).overlay(colorObtained)
            case ValueResult.ExpectedOnly(expected, _) => fansi.Str(expected).overlay(colorExpected)
          }
      }
  }

  private val dummyColoSeparator = Str(" ").overlay(Color.Reset)
  private def colorOnMatchType(
    str: Str,
    matchType: PairType,
  ): Str = {
    // Because SBT (and maybe other tools) put their own logging prefix on each line (e.g. [info])
    // they effectively resets the color of each line. Therefore we need to ensure every line is "recolored"
    // Because we always indent with spaces, we do this by recoloring the first space we find on each line.
    def recolorEachLine(orig: Str, color: EscapeAttr) = {
      orig.plainText.linesIterator
        .map { s =>
          if (s.startsWith(" "))
            dummyColoSeparator ++ Str(s.drop(1)).overlay(color)
          else
            Str(s).overlay(color)
        }
        .reduceLeft((accum, next) => accum ++ Str("\n") ++ next)
    }

    matchType match {
      case PairType.Both => str
      case PairType.ObtainedOnly => {
        recolorEachLine(str, colorObtained)
      }
      case PairType.ExpectedOnly =>
        recolorEachLine(str, colorExpected)
    }
  }

  implicit private class IntExt(val i: Int) extends AnyVal {
    @inline
    def asSpaces: String = " " * (i * indentStep)

    @inline
    def asSpacesPlus1: String = " " * ((i + 1) * indentStep)
  }

}

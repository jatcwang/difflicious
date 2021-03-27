package difflicious

sealed trait CompareResult

object CompareResult {

  def fromBool(isIdentical: Boolean): CompareResult = {
    if (isIdentical) CompareResult.Identical else CompareResult.Different
  }

  case object Different extends CompareResult
  case object Identical extends CompareResult
  case object Ignored extends CompareResult

}

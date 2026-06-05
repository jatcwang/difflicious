package difflicious

sealed trait PairType

object PairType {
  sealed trait ObtainedOrExpected extends PairType
  
  case object Both extends PairType
  case object ObtainedOnly extends PairType with ObtainedOrExpected
  case object ExpectedOnly extends PairType with ObtainedOrExpected
}

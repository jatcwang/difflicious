package difflicious

sealed trait PairType

object PairType {
  case object Both extends PairType
  case object ObtainedOnly extends PairType
  case object ExpectedOnly extends PairType
}

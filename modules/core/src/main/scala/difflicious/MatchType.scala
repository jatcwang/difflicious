package difflicious

sealed trait MatchType

object MatchType {
  case object Both extends MatchType
  case object ObtainedOnly extends MatchType
  case object ExpectedOnly extends MatchType
}

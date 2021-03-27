package difflicious

// FIXME: doc
sealed trait MatchType

object MatchType {
  case object Both extends MatchType
  case object ActualOnly extends MatchType
  case object ExpectedOnly extends MatchType
}

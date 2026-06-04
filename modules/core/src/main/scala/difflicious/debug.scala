package difflicious

object debug {

  sealed trait LogDerivation
  object LogDerivation extends LogDerivation

  object implicits {
    implicit val logDerivation: LogDerivation = LogDerivation
  }
}

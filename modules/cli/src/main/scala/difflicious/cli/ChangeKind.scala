package difflicious.cli

sealed trait ChangeKind {
  def name: String
}

object ChangeKind {
  case object Changed extends ChangeKind {
    override val name: String = "changed"
  }

  case object TypeMismatch extends ChangeKind {
    override val name: String = "type_mismatch"
  }

  case object ObtainedOnly extends ChangeKind {
    override val name: String = "obtained_only"
  }

  case object ExpectedOnly extends ChangeKind {
    override val name: String = "expected_only"
  }

  case object Ignored extends ChangeKind {
    override val name: String = "ignored"
  }
}

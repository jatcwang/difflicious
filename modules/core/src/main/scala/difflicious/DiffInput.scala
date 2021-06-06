package difflicious

/**
  * Input for diffing. We can either have both the left side (obtained) and right side (expected),
  * or just one side.
  * @tparam A
  */
sealed trait DiffInput[A] {
  def map[B](f: A => B): DiffInput[B] = {
    this match {
      case DiffInput.ObtainedOnly(obtained)   => DiffInput.ObtainedOnly(f(obtained))
      case DiffInput.ExpectedOnly(expected)   => DiffInput.ExpectedOnly(f(expected))
      case DiffInput.Both(obtained, expected) => DiffInput.Both(f(obtained), f(expected))
    }
  }
}

object DiffInput {
  final case class ObtainedOnly[A](obtained: A) extends DiffInput[A]
  final case class ExpectedOnly[A](expected: A) extends DiffInput[A]
  final case class Both[A](obtained: A, expected: A) extends DiffInput[A]
}

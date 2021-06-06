package difflicious.utils

trait SetLike[F[_]] {
  def asSet[A](f: F[A]): Set[A]
}

object SetLike {
  implicit def stdSetAsSet[F[AA] <: Set[AA]]: SetLike[F] = new SetLike[F] {
    override def asSet[A](f: F[A]): Set[A] = f
  }
}

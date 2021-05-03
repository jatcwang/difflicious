package difflicious.utils

// FIXME: add contramap
trait AsSet[F[_]] {
  def asSet[A](f: F[A]): Set[A]
}

object AsSet {
  implicit def stdSetAsSet[F[AA] <: Set[AA]]: AsSet[F] = new AsSet[F] {
    override def asSet[A](f: F[A]): Set[A] = f
  }
}

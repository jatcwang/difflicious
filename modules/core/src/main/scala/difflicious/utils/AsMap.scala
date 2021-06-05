package difflicious.utils

// FIXME: rename to MapLike?
trait AsMap[M[_, _]] {
  def asMap[A, B](m: M[A, B]): Map[A, B]
}

object AsMap {
  implicit def stdMapAsMap[M[AA, BB] <: Map[AA, BB]]: AsMap[M] = new AsMap[M] {
    override def asMap[A, B](m: M[A, B]): Map[A, B] = m
  }
}

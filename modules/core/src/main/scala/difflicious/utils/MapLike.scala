package difflicious.utils

trait MapLike[M[_, _]] {
  def asMap[A, B](m: M[A, B]): Map[A, B]
}

object MapLike {
  implicit def stdMapAsMap[M[AA, BB] <: Map[AA, BB]]: MapLike[M] = new MapLike[M] {
    override def asMap[A, B](m: M[A, B]): Map[A, B] = m
  }
}

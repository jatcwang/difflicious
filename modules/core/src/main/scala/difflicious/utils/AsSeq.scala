package difflicious.utils

trait AsSeq[F[_]] {
  def asSeq[A](f: F[A]): Seq[A]
}

object AsSeq {
  implicit def stdSeqAsSeq[F[AA] <: Seq[AA]]: AsSeq[F] = new AsSeq[F] {
    override def asSeq[A](f: F[A]): Seq[A] = f
  }
}

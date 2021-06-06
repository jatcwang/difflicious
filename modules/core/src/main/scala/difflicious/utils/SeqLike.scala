package difflicious.utils

trait SeqLike[F[_]] {
  def asSeq[A](f: F[A]): Seq[A]
}

object SeqLike {
  implicit def stdSeqAsSeq[F[AA] <: Seq[AA]]: SeqLike[F] = new SeqLike[F] {
    override def asSeq[A](f: F[A]): Seq[A] = f
  }
}

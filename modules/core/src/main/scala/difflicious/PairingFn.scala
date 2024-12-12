package difflicious

sealed trait PairingFn[A, B] {
  def fn: A => B
  def matching(a1: A, a2: A)(differ: Differ[A]): Boolean
}

object PairingFn {
  def lift[A, B](fn: A => B): PairingFn[A, B] = UsingEquals(fn)

  def approximate[A](threshold: Int): PairingFn[A, A] =
    Approximate(identity, differenceCountThreshold = threshold)

  case class UsingEquals[A, B](fn: A => B) extends PairingFn[A, B] {
    override def matching(a1: A, a2: A)(differ: Differ[A]): Boolean = fn(a1) == fn(a2)
  }

  sealed trait DifferBased[A, B] extends PairingFn[A, B] {
    def fn: A => B
    def differenceCountThreshold: Int
  }

  case class Approximate[A](fn: A => A, differenceCountThreshold: Int) extends DifferBased[A, A] {
    override def matching(a1: A, a2: A)(differ: Differ[A]): Boolean = {
      val diffResult = differ.diff(fn(a1), fn(a2))

      diffResult.differenceCount - diffResult.ignoredCount <= differenceCountThreshold
    }
  }

  case class Custom[A, B](fn: A => B, differenceCountThreshold: Int, pairDiffer: Differ[B]) extends DifferBased[A, B] {
    override def matching(a1: A, a2: A)(differ: Differ[A]): Boolean = {
      val diffResult = pairDiffer.diff(fn(a1), fn(a2))

      diffResult.differenceCount - diffResult.ignoredCount <= differenceCountThreshold
    }
  }
}

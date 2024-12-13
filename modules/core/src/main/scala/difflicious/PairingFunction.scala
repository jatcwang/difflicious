package difflicious

sealed trait PairingFunction[A, B]

object PairingFunction {
  def lift[A, B](fn: A => B): PairingFunction[A, B] = UsingEquals(fn)

  def approximative[A](threshold: Int): PairingFunction[A, A] =
    Approximative(differenceCountThreshold = threshold)

  case class UsingEquals[A, B](fn: A => B) extends PairingFunction[A, B] {
    def matching(a1: A, a2: A): Boolean = fn(a1) == fn(a2)
  }

  sealed trait DifferBased[A, B] extends PairingFunction[A, B] {
    def differenceCountThreshold: Int
  }

  case class Approximative[A](differenceCountThreshold: Int) extends DifferBased[A, A] {
    def matching(a1: A, a2: A)(diffResult: Differ[A]#R): Boolean =
      diffResult.differenceCount - diffResult.ignoredCount <= differenceCountThreshold

  }

  case class Custom[A, B](fn: A => B, differenceCountThreshold: Int, pairDiffer: Differ[B]) extends DifferBased[A, B] {
    def matching(a1: A, a2: A): Boolean = {
      val diffResult = pairDiffer.diff(fn(a1), fn(a2))

      diffResult.differenceCount - diffResult.ignoredCount <= differenceCountThreshold
    }
  }
}

package difflicious

import izumi.reflect.macrortti.LTag

/**
  * The configuration change operation we want to perform on a differ.
  * For example we might want to:
  *
  * - Mark the current differ as ignored so its comparison never fails
  * - Change a SeqDiffer to pair by a field instead of index
  */
sealed trait ConfigureOp

object ConfigureOp {
  val ignore: SetIgnored = SetIgnored(true)
  val unignore: SetIgnored = SetIgnored(false)

  final case class SetIgnored(isIgnored: Boolean) extends ConfigureOp
//  final case class Replace[A](newDiffer: Differ[A], aTag: LTag[A]) extends ConfigureOp
  sealed trait PairBy[-A] extends ConfigureOp
  object PairBy {
    case object Index extends PairBy[Any]
    final case class ByFunc[A, B] private[difflicious] (func: A => B, aTag: LTag[A]) extends PairBy[A]

    def func[A, B](func: A => B)(implicit aTag: LTag[A]): ByFunc[A, B] = ByFunc(func, aTag)
  }

}

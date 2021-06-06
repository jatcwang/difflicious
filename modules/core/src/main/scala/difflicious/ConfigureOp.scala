package difflicious

import izumi.reflect.macrortti.LTag

/**
  * The configuration change operation we want to perform on a differ.
  * For example we might want to:
  *
  * - Mark the current differ as ignored so its comparison never fails
  * - Change a Differ for Seq to pair by a field instead of index
  */
sealed trait ConfigureOp

object ConfigureOp {
  val ignore: SetIgnored = SetIgnored(true)
  val unignore: SetIgnored = SetIgnored(false)

  final case class SetIgnored(isIgnored: Boolean) extends ConfigureOp
  final case class TransformDiffer[T](func: Differ[T] => Differ[T], tag: LTag[T]) extends ConfigureOp {
    def unsafeCastFunc[X]: Differ[X] => Differ[X] = func.asInstanceOf[Differ[X] => Differ[X]]
  }
  sealed trait PairBy[-A] extends ConfigureOp
  object PairBy {
    case object Index extends PairBy[Any]
    final case class ByFunc[A, B] private[difflicious] (func: A => B, aTag: LTag[A]) extends PairBy[A]
  }

}

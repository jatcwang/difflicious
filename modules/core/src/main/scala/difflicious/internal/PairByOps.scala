package difflicious.internal

import difflicious.utils.Pairable
import difflicious.{ConfigurePath, Differ}
import difflicious.ConfigureOp.PairBy
import difflicious.internal.EitherGetSyntax.EitherExtensionOps

// pairBy has to be defined differently for better type inference.
final class PairByOps[F[_], A](differ: Differ[F[A]]) {
  def pairBy[B](f: A => B): Differ[F[A]] =
    differ.configureRaw(ConfigurePath.current, PairBy.ByFunc(f)).unsafeGet

  def pairByIndex: Differ[F[A]] =
    differ.configureRaw(ConfigurePath.current, PairBy.Index).unsafeGet
}

trait ToPairByOps {
  implicit def toPairByOps[F[_]: Pairable, A](differ: Differ[F[A]]): PairByOps[F, A] = new PairByOps(differ)
}

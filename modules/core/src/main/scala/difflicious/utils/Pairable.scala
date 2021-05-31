package difflicious.utils

/**
  * A marker typeclass for some container that we can use pairBy when diffing
  */
// $COVERAGE-OFF$
trait Pairable[F[_]]

object Pairable extends PairableInstances {}

trait PairableInstances {
  implicit def seqPairable[F[X] <: Seq[X]]: Pairable[F] = new Pairable[F] {}
  implicit def setPairable[F[X] <: Set[X]]: Pairable[F] = new Pairable[F] {}
}
// $COVERAGE-ON$

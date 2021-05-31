package difflicious.utils

import scala.annotation.nowarn

/**
  * A marker typeclass for some container that we can use pairBy when diffing
  */
// $COVERAGE-OFF$
trait Pairable[F[_]]

object Pairable extends PairableInstances {}

@nowarn("msg=.*never used.*")
trait PairableInstances {
  implicit def seqPairable[F[_]: AsSeq]: Pairable[F] = new Pairable[F] {}
  implicit def setPairable[F[_]: AsSet]: Pairable[F] = new Pairable[F] {}
}
// $COVERAGE-ON$

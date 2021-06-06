package difflicious.utils

import difflicious.utils.Eachable.EachableOps

import scala.annotation.{compileTimeOnly, nowarn}

// $COVERAGE-OFF$
trait Eachable[F[_]]

object Eachable extends EachableInstances {

  trait EachableOps[F[_], A] {
    @compileTimeOnly("each should only be called inside Differ configuration methods with path elements")
    def each: A = sys.error("each should only be called inside Differ configuration methods with path elements")
  }

}

@nowarn("msg=.*never used.*")
trait EachableInstances {
  implicit def seqEachable[F[_]: SeqLike]: Eachable[F] = new Eachable[F] {}

  implicit def setEachable[F[_]: SetLike]: Eachable[F] = new Eachable[F] {}

  // Instance for Map directly for better inference
  implicit def mapEachable[K]: Eachable[Map[K, *]] = new Eachable[Map[K, *]] {}
}

trait ToEachableOps {
  @nowarn("msg=.*never used.*")
  implicit def toEachableOps[F[_]: Eachable, A](fa: F[A]): EachableOps[F, A] = new EachableOps[F, A] {}
}
// $COVERAGE-ON$

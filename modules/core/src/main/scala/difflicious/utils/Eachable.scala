package difflicious.utils

import difflicious.utils.Eachable.EachableOps

import scala.annotation.{compileTimeOnly, nowarn}

// $COVERAGE-OFF$
trait Eachable0[A, Element]
trait Eachable1[F[_]]
trait Eachable2[F[_, _]]

object Eachable extends EachableInstances {

  trait EachableOps[A] {
    @compileTimeOnly("each should only be called inside Differ configuration methods with path elements")
    def each: A = sys.error("each should only be called inside Differ configuration methods with path elements")
  }

}

@nowarn("msg=.*never used.*")
trait EachableInstances {
  implicit def seqEachable[F[_]: SeqLike]: Eachable1[F] = new Eachable1[F] {}

  implicit def setEachable[F[_]: SetLike]: Eachable1[F] = new Eachable1[F] {}

  implicit val mapEachable: Eachable2[Map] = new Eachable2[Map] {}
}

trait ToEachableOps {
  @nowarn("msg=.*never used.*")
  implicit def toEachable0Ops[A, Element](a: A)(implicit eachable: Eachable0[A, Element]): EachableOps[Element] =
    new EachableOps[Element] {}

  @nowarn("msg=.*never used.*")
  implicit def toEachable1Ops[F[_]: Eachable1, A](fa: F[A]): EachableOps[A] = new EachableOps[A] {}

  @nowarn("msg=.*never used.*")
  implicit def toEachable2Ops[F[_, _]: Eachable2, K, V](fa: F[K, V]): EachableOps[V] = new EachableOps[V] {}
}
// $COVERAGE-ON$

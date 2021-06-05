package difflicious.utils

import difflicious.utils.Eachable.EachableOps
import difflicious.utils.Eachable2.Eachable2Ops

import scala.annotation.{compileTimeOnly, nowarn}

// $COVERAGE-OFF$
trait Eachable[F[_]]

object Eachable extends EachableInstances {

  trait EachableOps[F[_], A] {
    @compileTimeOnly("each should only be called inside Differ configuration methods with path elements")
    def each: A = sys.error("each should only be called inside Differ configuration methods with path elements")
  }

}

trait Eachable2[F[_, _]]

object Eachable2 extends Eachable2Instances {
  trait Eachable2Ops[F[_, _], A] {
    @compileTimeOnly("each should only be called inside Differ configuration methods with path elements")
    def each: A = sys.error("each should only be called inside Differ configuration methods with path elements")
  }
}

@nowarn("msg=.*never used.*")
trait EachableInstances {
  implicit def seqEachable[F[_]: AsSeq]: Eachable[F] = new Eachable[F] {}

  implicit def setEachable[F[_]: AsSet]: Eachable[F] = new Eachable[F] {}
}

@nowarn("msg=.*never used.*")
trait Eachable2Instances {
  implicit def mapEachable2[F[_, _]: AsMap, K, V]: Eachable2[Map] = new Eachable2[Map] {}
}

trait ToEachableOps {
  @nowarn("msg=.*never used.*")
  implicit def toEachableOps[F[_]: Eachable, A](fa: F[A]): EachableOps[F, A] = new EachableOps[F, A] {}

  @nowarn("msg=.*never used.*")
  implicit def toEachable2Ops[F[_, _]: Eachable2, A, B](fa: F[A, B]): Eachable2Ops[F, B] = new Eachable2Ops[F, B] {}
}
// $COVERAGE-ON$

package difflicious.utils

import difflicious.utils.Eachable.EachableOps

import scala.annotation.{compileTimeOnly, nowarn}

trait Eachable[F[_]] {
  @compileTimeOnly("each should only be called inside Differ.configure*")
  def each[A](fa: F[A]): A
}

object Eachable extends EachableInstances {

  trait EachableOps[F[_], A] {
    @compileTimeOnly("each should only be called inside Differ.configure*")
    def each: A = sys.error("each should only be called inside Differ.configure*")
  }

}

trait EachableInstances {
  implicit def seqEachable[F[X] <: Seq[X]]: Eachable[F] = new Eachable[F] {
    override def each[A](fa: F[A]): A = sys.error("each should only be called inside Differ.configure*")
  }

  implicit def setEachable[F[X] <: Set[X]]: Eachable[F] = new Eachable[F] {
    override def each[A](fa: F[A]): A = sys.error("each should only be called inside Differ.configure*")
  }

  implicit def mapEachable[F[KK, VV] <: Map[KK, VV], K, V]: Eachable[Map[K, *]] = new Eachable[Map[K, *]] {
    override def each[A](fa: Map[K, A]): A = sys.error("each should only be called inside Differ.configure*")
  }
}

trait ToEachableOps {
  @nowarn("msg=.*never used.*")
  implicit def toEachableOps[F[_]: Eachable, A](fa: F[A]): EachableOps[F, A] = new EachableOps[F, A] {}
}

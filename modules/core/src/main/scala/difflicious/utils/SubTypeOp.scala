package difflicious.utils

import difflicious.DifferSubTypeRelationship

import scala.annotation.{compileTimeOnly, nowarn}

// $COVERAGE-OFF$
trait SubTypeOp[A] {
  @compileTimeOnly("subType should only be called in a Differ.configure* path")
  def subType[B <: A]: B =
    sys.error("subType should only be called as part of path in Differ.configure*")

  @compileTimeOnly("subType should only be called in a Differ.configure* path")
  def subType[B](implicit relationship: DifferSubTypeRelationship[A, B]): B =
    sys.error("subType should only be called as part of path in Differ.configure*")
}

trait ToSubTypeOp {

  @nowarn("msg=.*never used.*")
  implicit def toSubTypeOp[A](a: A): SubTypeOp[A] = new SubTypeOp[A] {}
}
// $COVERAGE-ON$

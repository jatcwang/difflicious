package difflicious.internal
import difflicious.Differ
import scala.annotation.nowarn

trait ConfigureMethods[T]:
  this: Differ[T] =>

  inline def ignoreAt[U](path: T => U): Differ[T] = ??? // FIXME:

  inline def configure[U](path: T => U)(configFunc: Differ[U] => Differ[U]): Differ[T] = ??? // FIXME:

  inline def replace[U](path: T => U)(newDiffer: Differ[U]): Differ[T] = ??? // FIXME:


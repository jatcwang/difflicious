trait ConfigureMethods[T]:
  this => Differ[T]

  def ignoreAt[U](path: T => U): Differ[T]

  def configure[U](path: T => U)(configFunc: Differ[U] => Differ[U]): Differ[T] =

  def replace[U](path: T => U)(newDiffer: Differ[U]): Differ[T] =

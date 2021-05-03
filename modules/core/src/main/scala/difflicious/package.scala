package object difflicious {
  type Vec[+A] = scala.collection.immutable.Vector[A]
  val Vec: Vector.type = scala.collection.immutable.Vector

  def checkDiff[A](obtained: A, expected: A)(implicit d: Differ[A]): DiffResult = d.diff(obtained, expected)
}

package difflicious.testutils

import difflicious.Differ

// FIXME: ?
object testtypes {
  final case class Big(
    i: Int,
    s: String,
//    map: Map[Key, Dog],
//    list: Vector[Dog],
//    set: Set[Dog],
  )

  object Big {
    implicit val diff: Differ[Big] = Differ.derive[Big]
  }

  final case class Dog(
    name: String,
    age: Double,
  )

  final case class Key(
    name: String,
    x: Int,
  )
}

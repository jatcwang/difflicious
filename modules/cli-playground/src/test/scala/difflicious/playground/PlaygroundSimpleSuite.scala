package difflicious.playground

import difflicious.Differ
import difflicious.scalatest.ScalatestDiff._
import org.scalatest.funsuite.AnyFunSuite

final class PlaygroundSimpleSuite extends AnyFunSuite {
  import PlaygroundSimpleSuite._

  test("list of case class values has a missing whole value") {
    Differ[List[User]].assertNoDiff(
      obtained = List(
        User(id = 1, name = "Ada", email = "ada@example.com"),
        User(id = 3, name = "Linus", email = "linus@example.com"),
      ),
      expected = List(
        User(id = 1, name = "Ada", email = "ada@example.com"),
        User(id = 2, name = "Grace", email = "grace@example.com"),
        User(id = 3, name = "Linus", email = "linus@example.com"),
      ),
    )
  }

  test("another test that fails") {
    Differ[List[Dog]].assertNoDiff(
      obtained = List(
        Dog(id = 1, name = "Alice", age = 5),
        Dog(id = 3, name = "Charly", age = 7),
      ),
      expected = List(
        Dog(id = 1, name = "Alice", age = 5),
        Dog(id = 2, name = "Bob", age = 6),
        Dog(id = 3, name = "Charlie", age = 7),
      ),
    )
  }
}

object PlaygroundSimpleSuite {
  final case class User(id: Long, name: String, email: String) derives Differ

  final case class Dog(id: Long, name: String, age: Int) derives Differ
}

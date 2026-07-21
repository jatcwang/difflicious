package example

import difflicious.Differ
import difflicious.munit.MUnitDiffliciousSuite
import munit.FunSuite

final class MUnitExampleSuite extends FunSuite with MUnitDiffliciousSuite {
  import MUnitExampleSuite._

  test("user profile matches") {
    Differ[UserProfile].assertNoDiff(
      obtained = UserProfile(name = "Ada Lovelace", email = "ada@old.example.com"),
      expected = UserProfile(name = "Ada Lovelace", email = "ada@example.com"),
    )
  }
}

object MUnitExampleSuite {
  final case class UserProfile(name: String, email: String) derives Differ
}

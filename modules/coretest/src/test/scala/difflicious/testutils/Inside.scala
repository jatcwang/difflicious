package difflicious.testutils
import munit.Assertions._

object Inside {

  // similar to scalatest's inside
  def inside[A](value: A)(pf: PartialFunction[A, Unit]): Unit = {
    if (pf.isDefinedAt(value)) {
      pf.apply(value)
    } else {
      fail(s"inside did not match for value: $value")
    }
  }

}

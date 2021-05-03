package difflicious

import cats.kernel.Order
import io.circe._
import io.circe.syntax._
import org.scalacheck.Arbitrary
import difflicious.Differ.ValueDiffer

object testtypes {

  case class CC(i: Int, s: String, dd: Double)

  object CC {
    implicit val arb: Arbitrary[CC] = Arbitrary(for {
      i <- Arbitrary.arbitrary[Int]
      s <- Arbitrary.arbitrary[String]
      dd <- Arbitrary.arbitrary[Double]
    } yield CC(i, s, dd))

    implicit val differ: Differ[CC] = Differ.derive[CC]

    implicit val order: Order[CC] = Order.by(a => (a.i, a.s, a.dd))
  }

  sealed trait Foo

  object Foo {
    implicit val differ: Differ[Foo] = Differ.derive[Foo]
  }

  case class Sub1(i: Int) extends Foo

  sealed trait Foo2 extends Foo
  case class SubSub1(d: Double) extends Foo2
  case class SubSub2(i: Int) extends Foo2

  final case class MapKey(a: Int, b: String)

  object MapKey {
    private implicit val encoder: Encoder[MapKey] = value => Json.obj("a" -> value.a.asJson, "bb" -> value.b.asJson)
    implicit val differ: ValueDiffer[MapKey] = Differ.useEquals

    implicit val arb: Arbitrary[MapKey] = Arbitrary(for {
      a <- Arbitrary.arbitrary[Int]
      b <- Arbitrary.arbitrary[String]
    } yield MapKey(a, b))
  }

}

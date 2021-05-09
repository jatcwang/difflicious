package difflicious

import cats.kernel.Order
import io.circe._
import io.circe.syntax._
import org.scalacheck.{Arbitrary, Gen}
import difflicious.Differ.{ValueDiffer, EqualsDiffer}

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

  final case class EqClass(int: Int)

  object EqClass {
    implicit val arb: Arbitrary[EqClass] = Arbitrary.apply(Arbitrary.arbitrary[Int].map(EqClass(_)))

    private implicit val encoder: Encoder[EqClass] = c =>
      Json.obj(
        "int" := c.int,
      )

    implicit val differ: EqualsDiffer[EqClass] = Differ.useEquals[EqClass]
  }

  sealed trait Sealed

  object Sealed {
    implicit val differ: Differ[Sealed] = Differ.derive[Sealed]

    private val genSub1: Gen[Sub1] = Arbitrary.arbitrary[Int].map(Sub1)
    private val genSubSub1: Gen[SubSub1] = Arbitrary.arbitrary[Double].map(SubSub1(_))
    private val genSubSub2: Gen[SubSub2] = Arbitrary.arbitrary[List[CC]].map(SubSub2)

    implicit val arb: Arbitrary[Sealed] = Arbitrary(
      Gen.oneOf(
        genSub1,
        genSubSub1,
        genSubSub2,
      ),
    )
  }

  case class Sub1(i: Int) extends Sealed

  sealed trait SubSealed extends Sealed
  case class SubSub1(d: Double) extends SubSealed
  case class SubSub2(list: List[CC]) extends SubSealed

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

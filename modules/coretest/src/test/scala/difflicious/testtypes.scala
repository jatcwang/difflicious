package difflicious

import cats.data.Ior
import cats.kernel.Order
import org.scalacheck.{Gen, Arbitrary}
import difflicious.Differ.{ValueDiffer, EqualsDiffer}
import izumi.reflect.macrortti.LTag

object testtypes {

  // Dummy differ that fails when any of its method is called. For tests where we just need a Differ[T]
  def dummyDiffer[T]: Differ[T] = new Differ[T] {
    override def diff(inputs: Ior[T, T]): R = sys.error("diff on dummyDiffer")

    override def configureRaw(path: ConfigurePath, operation: ConfigureOp): Either[DifferUpdateError, Differ[T]] =
      sys.error("configureRaw on dummyDiffer")
  }

  case class HasASeq[A](seq: Seq[A])

  object HasASeq {
    // FIXME: feels like this needs too many implicits
    implicit def differ[A](
      implicit seqTag: LTag[Seq[A]],
      differ: Differ[A],
      aTag: LTag[A],
      hasASeqTag: LTag[HasASeq[A]],
    ): Differ[HasASeq[A]] = {
      Differ.derive[HasASeq[A]]
    }
  }

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

    implicit val differ: EqualsDiffer[EqClass] = Differ.useEquals[EqClass](_.toString)
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

  final case class `Weird@Sub`(i: Int, `weird@Field`: String) extends Sealed

  sealed trait SubSealed extends Sealed
  case class SubSub1(d: Double) extends SubSealed
  case class SubSub2(list: List[CC]) extends SubSealed

  final case class MapKey(a: Int, b: String)

  trait OpenSuperType

  object OpenSuperType {
    implicit val differ: Differ[OpenSuperType] = dummyDiffer[OpenSuperType]
  }

  final case class OpenSub(i: Int) extends OpenSuperType

  object MapKey {
    implicit val differ: ValueDiffer[MapKey] = Differ.useEquals(_.toString)

    implicit val arb: Arbitrary[MapKey] = Arbitrary(for {
      a <- Arbitrary.arbitrary[Int]
      b <- Arbitrary.arbitrary[String]
    } yield MapKey(a, b))
  }

}

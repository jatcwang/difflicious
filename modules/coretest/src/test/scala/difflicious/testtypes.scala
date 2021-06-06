package difflicious

import cats.kernel.Order
import org.scalacheck.{Gen, Arbitrary}
import difflicious.differ.{ValueDiffer, EqualsDiffer}
import difflicious.testtypes.SubSealed.{SubSub1, SubSub2}
import izumi.reflect.macrortti.LTag

object testtypes {

  // Dummy differ that fails when any of its method is called. For tests where we just need a Differ[T]
  def dummyDiffer[T](implicit tTag: LTag[T]): Differ[T] = new Differ[T] {
    override def diff(inputs: DiffInput[T]): R = sys.error("diff on dummyDiffer")

    override def configureIgnored(newIgnored: Boolean): Differ[T] =
      sys.error("dummyDiffer methods should not be called")

    override def configurePath(
      step: String,
      nextPath: ConfigurePath,
      op: ConfigureOp,
    ): Either[ConfigureError, Differ[T]] = sys.error("dummyDiffer methods should not be called")

    override def configurePairBy(path: ConfigurePath, op: ConfigureOp.PairBy[_]): Either[ConfigureError, Differ[T]] =
      sys.error("dummyDiffer methods should not be called")

    override def tag: LTag[T] = tTag

  }

  case class HasASeq[A](seq: Seq[A])

  object HasASeq {
    implicit def differ[A](
      implicit differ: Differ[Seq[A]],
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

  sealed trait SealedWithCustom

  object SealedWithCustom {
    case class Custom(i: Int) extends SealedWithCustom
    object Custom {
      implicit val differ: Differ[Custom] = Differ.derive[Custom].ignoreAt(_.i)
    }
    case class Normal(i: Int) extends SealedWithCustom

    implicit val differ: Differ[SealedWithCustom] = Differ.derive[SealedWithCustom]
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

  object SubSealed {
    case class SubSub1(d: Double) extends SubSealed
    case class SubSub2(list: List[CC]) extends SubSealed
  }

  final case class MapKey(a: Int, b: String)

  object MapKey {
    implicit val differ: ValueDiffer[MapKey] = Differ.useEquals(_.toString)

    implicit val arb: Arbitrary[MapKey] = Arbitrary(for {
      a <- Arbitrary.arbitrary[Int]
      b <- Arbitrary.arbitrary[String]
    } yield MapKey(a, b))

    implicit val order: Order[MapKey] = Order.by(mk => (mk.a, mk.b))
  }

  trait OpenSuperType

  object OpenSuperType {
    implicit val differ: Differ[OpenSuperType] = dummyDiffer[OpenSuperType]
  }

  final case class OpenSub(i: Int) extends OpenSuperType

}

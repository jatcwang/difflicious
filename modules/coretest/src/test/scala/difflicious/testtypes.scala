package difflicious

import cats.kernel.Order
import org.scalacheck.{Gen, Arbitrary}
import difflicious.differ.{ValueDiffer, EqualsDiffer}

object testtypes extends ScalaVersionDependentTestTypes {

  // Dummy differ that fails when any of its method is called. For tests where we just need a Differ[T]
  def dummyDiffer[T]: Differ[T] = new Differ[T] {
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

  }

  case class HasASeq[A](seq: Seq[A])

  object HasASeq {
    implicit def differ[A](implicit
      differ: Differ[Seq[A]],
    ): Differ[HasASeq[A]] = {
      Differ.derived[HasASeq[A]]
    }
  }

  case class CC(i: Int, s: String, dd: Double)

  object CC {
    implicit val arb: Arbitrary[CC] = Arbitrary(for {
      i <- Arbitrary.arbitrary[Int]
      s <- Arbitrary.arbitrary[String]
      dd <- Arbitrary.arbitrary[Double]
    } yield CC(i, s, dd))

    implicit val differ: Differ[CC] = Differ.derived[CC]

    implicit val order: Order[CC] = Order.by(a => (a.i, a.s, a.dd))
  }

  final case class EqClass(int: Int)

  object EqClass {
    implicit val arb: Arbitrary[EqClass] = Arbitrary.apply(Arbitrary.arbitrary[Int].map(EqClass(_)))

    implicit val differ: EqualsDiffer[EqClass] = Differ.useEquals[EqClass](_.toString)
  }

  final case class NewInt(int: Int)

  object NewInt {
    implicit val arb: Arbitrary[NewInt] = Arbitrary.apply(Arbitrary.arbitrary[Int].map(NewInt(_)))

    implicit val differ: ValueDiffer[NewInt] = Differ.intDiffer.contramap(_.int)
  }

  sealed trait Sealed

  object Sealed {
    case class Sub1(i: Int) extends Sealed
    final case class Sub2(d: Double) extends Sealed
    final case class Sub3(list: List[CC]) extends Sealed
    final case class `Weird@Sub`(i: Int, `weird@Field`: String) extends Sealed

    implicit val differ: Differ[Sealed] = Differ.derived[Sealed]

    private val genSub1: Gen[Sub1] = Arbitrary.arbitrary[Int].map(Sub1.apply)
    private val genSub2: Gen[Sub2] = Arbitrary.arbitrary[Double].map(Sub2.apply)
    private val genSub3: Gen[Sub3] = Arbitrary.arbitrary[List[CC]].map(Sub3.apply)

    implicit val arb: Arbitrary[Sealed] = Arbitrary(
      Gen.oneOf(
        genSub1,
        genSub2,
        genSub3,
      ),
    )
  }

  sealed trait SealedWithCustom

  object SealedWithCustom {
    case class Custom(i: Int) extends SealedWithCustom
    object Custom {
      implicit val differ: Differ[Custom] = Differ.derived[Custom].ignoreAt(_.i)
    }
    case class Normal(i: Int) extends SealedWithCustom

    implicit val differ: Differ[SealedWithCustom] = Differ.derived[SealedWithCustom]
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

  case class AlwaysIgnoreClass(i: Int)

  object AlwaysIgnoreClass {
    implicit val differ: AlwaysIgnoreDiffer[AlwaysIgnoreClass] = Differ.alwaysIgnore
  }

}

package difflicious

import difflicious.Differ
import org.scalacheck.{Arbitrary, Gen}

trait ScalaVersionDependentTestTypes {
  sealed trait SealedNested

  object SealedNested {
    case class SubFoo(i: Int) extends SealedNested

    sealed trait SubSealed extends SealedNested
    object SubSealed {
      case class SubSub1(d: Double) extends SubSealed
      case class SubSub2(s: String) extends SubSealed
    }

    import SubSealed._
    implicit val arb: Arbitrary[SealedNested] = {
      val subFoo = Gen.posNum[Int].map(SubFoo.apply)
      val subsub1 = Gen.posNum[Double].map(SubSub1.apply)
      val subsub2 = Gen.alphaStr.map(SubSub2.apply)

      Arbitrary(Gen.oneOf(subFoo, subsub1, subsub2))
    }
    implicit val differ: Differ[SealedNested] = Differ.derived[SealedNested]
  }

}

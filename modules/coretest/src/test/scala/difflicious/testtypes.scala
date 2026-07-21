package difflicious

import cats.kernel.Order
import org.scalacheck.{Gen, Arbitrary}
import difflicious.differ.{ValueDiffer, EqualsDiffer}

object testtypes extends ScalaVersionDependentTestTypes {

  // Dummy differ that fails when any of its method is called. For tests where we just need a Differ[T]
  def dummyDiffer[T]: Differ[T] = new Differ[T] {
    override val canUseEquals: Boolean = false

    override def diff(inputs: DiffInput[T]): R = sys.error("diff on dummyDiffer")

    override def configureIgnored(newIgnored: Boolean): Differ[T] =
      sys.error("dummyDiffer methods should not be called")

    override def configurePath(
      step: String,
      nextPath: ConfigurePath,
      op: ConfigureOp,
    ): Either[ConfigureError, Differ[T]] = sys.error("dummyDiffer methods should not be called")

    override def configurePairBy(path: ConfigurePath, op: ConfigureOp.PairBy[?]): Either[ConfigureError, Differ[T]] =
      sys.error("dummyDiffer methods should not be called")

  }

  case class HasASeq[A](seq: Seq[A])

  object HasASeq {
    implicit def differ[A](implicit
      differ: Differ[A],
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

  // Product types without companion Differ instances, used by derivation tests.
  final case class SimpleCaseClass1(value: String)
  final case class SimpleCaseClass2(value: String)
  final case class SimpleCaseClass3(value: String)
  case object SimpleCaseObject
  final case class SimpleCaseClassSubject(
    p1: SimpleCaseClass1,
    p2: SimpleCaseClass2,
    p3: SimpleCaseClass3,
  )
  final case class SimpleCaseClassListSubject(values: List[SimpleCaseClass1])
  final case class SimpleGenericCaseClass[A](value: A)
  final case class SimpleGenericCaseClassSubject(value: SimpleGenericCaseClass[SimpleCaseClass1])
  final case class GenericBox[A](content: List[A])

  object GenericBox {
    implicit def differ[A](implicit contentDiffer: Differ[List[A]]): Differ[GenericBox[A]] =
      Differ.derived[GenericBox[A]]
  }

  final case class GenericFactory[A](boxes: List[GenericBox[A]])

  object GenericFactory {
    implicit def differ[A](implicit boxesDiffer: Differ[List[GenericBox[A]]]): Differ[GenericFactory[A]] =
      Differ.derived[GenericFactory[A]]
  }

  final case class RecursiveDerivedNode(value: String, children: List[RecursiveDerivedNode])
  final case class RecursiveDerivedDeepNode(value: String, children: List[RecursiveDerivedDeepNode])
  final case class CustomList[A](values: List[A])

  object CustomList {
    implicit val seqLike: difflicious.utils.SeqLike[CustomList] =
      new difflicious.utils.SeqLike[CustomList] {
        override def asSeq[A](f: CustomList[A]): Seq[A] = f.values
      }
  }

  final case class RecursiveNodeWithCustomList(
    nodes: CustomList[RecursiveNodeWithCustomList],
  )

  def recursiveNodeWithCustomList(children: RecursiveNodeWithCustomList*): RecursiveNodeWithCustomList =
    RecursiveNodeWithCustomList(CustomList(children.toList))

  trait SomeTrait
  trait SomeOtherTrait
  final case class DerivationFailureNested(
    value: SomeTrait,
    duplicateValue: SomeTrait,
    otherValue: SomeOtherTrait,
    list: List[SomeTrait],
  )
  final case class DerivationFailureSubject(
    missing: SomeTrait,
    duplicateMissing: SomeTrait,
    nested: DerivationFailureNested,
  )
  trait MissingMapKey
  trait MissingMapValue
  final case class MapDerivationFailureSubject(
    map: Map[MissingMapKey, MissingMapValue],
  )

  sealed trait DerivationFailureSealed

  object DerivationFailureSealed {
    final case class Direct(
      value: SomeTrait,
      duplicateValue: SomeTrait,
    ) extends DerivationFailureSealed
    final case class Nested(value: DerivationFailureNested) extends DerivationFailureSealed
  }

  sealed trait MutualSealedLeft

  object MutualSealedLeft {
    final case class Leaf(value: String) extends MutualSealedLeft
    final case class HasRight(value: String, right: MutualSealedRight) extends MutualSealedLeft
  }

  sealed trait MutualSealedRight

  object MutualSealedRight {
    final case class Leaf(value: String) extends MutualSealedRight
    final case class HasLeft(value: String, left: MutualSealedLeft) extends MutualSealedRight
  }

  final case class TreeCaseClass1(value: String)
  final case class TreeCaseClass2(value: String)
  final case class TreeContainer(
    c1: TreeCaseClass1,
    c2: TreeCaseClass2,
    option: Option[TreeCaseClass2],
    either: Either[TreeCaseClass1, TreeCaseClass2],
    map: Map[String, TreeCaseClass2],
    list: List[Option[TreeCaseClass2]],
  )

  sealed trait DeepSealed

  object DeepSealed {
    final case class UsesManual(value: SimpleCaseClass1) extends DeepSealed
    final case class UsesDerived(value: SimpleCaseClass2) extends DeepSealed
  }

  final case class DeepSealedSubject(value: DeepSealed)

  sealed trait GenericSealed[A]

  object GenericSealed {
    final case class Single[A](value: A) extends GenericSealed[A]
    final case class Many[A](values: List[A]) extends GenericSealed[A]
  }

  final case class GenericSealedSubject[A](value: GenericSealed[A])

  sealed trait MultiLevelSealed

  object MultiLevelSealed {
    final case class RootLeaf(value: String) extends MultiLevelSealed

    sealed trait Nested extends MultiLevelSealed

    object Nested {
      final case class NestedLeaf(value: String) extends Nested
      final case class NestedOther(value: Int) extends Nested
    }
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

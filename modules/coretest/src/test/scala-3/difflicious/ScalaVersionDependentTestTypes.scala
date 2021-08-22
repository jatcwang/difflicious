package difflicious

import org.scalacheck.{Arbitrary, Gen}

trait ScalaVersionDependentTestTypes:
  enum MyEnum {
    case I
    case V(i: Int)
  }

  object MyEnum:
    given Differ[MyEnum] = Differ.derived[MyEnum]

    given Arbitrary[MyEnum] = Arbitrary(
      Gen.oneOf(
        Gen.const(MyEnum.I),
        Gen.posNum[Int].map(MyEnum.V.apply),
      ),
    )

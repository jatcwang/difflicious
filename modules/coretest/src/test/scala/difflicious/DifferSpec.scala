package difflicious

import difflicious.DiffResultPrinter.consoleOutput
import difflicious.Differ.ValueDiffer
import munit.ScalaCheckSuite
import difflicious.implicits._
import io.circe.{Encoder, Json}
import io.circe.syntax._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._

// FIXME:
class DifferSpec extends ScalaCheckSuite {

  private val R = "\u001b[31m" // actual (red)
  private val G = "\u001b[32m" // expected (green)
  private val I = "\u001b[90m" // ignore (dark grey)
  private val X = "\u001b[39m" // terminal color reset
  private val grayIgnoredStr = s"$I[IGNORED]$X"
  // Sometimes the [IGNORE] field exist in a actual/expected-only object so it won't be colored
  private val justIgnoredStr = s"[IGNORED]"

  // FIXME: test deep path updates

  test("Map: isOk == true if two values are equal") {
    assertOkIfValuesEqualProp(Differ.mapDiffer[Map, MapKey, CC])
  }

  test("Map: isOk == false if two values are NOT equal") {
    assertNotOkIfNotEqualProp(Differ.mapDiffer[Map, MapKey, CC])
  }

  test("Map: isOk always true if differ is marked ignored") {
    assertIsOkIfIgnoredProp(Differ.mapDiffer[Map, MapKey, CC])
  }

  test("Map diff shows both matched entries (based on key equals) and also one-side-only entries") {
    assertConsoleDiffOutput(
      Differ.mapDiffer[Map, MapKey, CC].updateByStrPathOrFail(DifferOp.ignore, "each", "i"),
      Map(
        MapKey(1, "s") -> CC(1, "s1", 1),
        MapKey(2, "sa") -> CC(2, "s2", 2),
        MapKey(4, "diff") -> CC(1, "s4", 1),
      ),
      Map(
        MapKey(1, "s") -> CC(1, "s1", 1),
        MapKey(4, "diff") -> CC(1, "sx", 1),
        MapKey(3, "se") -> CC(3, "s3", 2),
      ),
      s"""Map(
         |  $R{"a":2,"bb":"sa"}$X -> ${R}CC(
         |      i: $justIgnoredStr,
         |      s: "s2",
         |      dd: 2.0,
         |    )$X,
         |  {"a":1,"bb":"s"} -> CC(
         |      i: $grayIgnoredStr,
         |      s: "s1",
         |      dd: 1.0,
         |    ),
         |  {"a":4,"bb":"diff"} -> CC(
         |      i: $grayIgnoredStr,
         |      s: $R"s4"$X -> $G"sx"$X,
         |      dd: 1.0,
         |    ),
         |  $G{"a":3,"bb":"se"}$X -> ${G}CC(
         |      i: $justIgnoredStr,
         |      s: "s3",
         |      dd: 2.0,
         |    )${X},
         |)""".stripMargin,
    )
  }

  test("Seq: isOk == true if two values are equal") {
    assertOkIfValuesEqualProp(Differ.seqDiffer[List, CC])
  }

  test("Seq: isOk == false if two values are not equal") {
    assertNotOkIfNotEqualProp(Differ.seqDiffer[List, CC])
  }

  test("Seq: isOk always true if differ is marked ignored") {
    assertIsOkIfIgnoredProp(Differ.seqDiffer[Seq, CC])
  }

  test("Seq match entries base on item index by default") {
    assertConsoleDiffOutput(
      Differ
        .seqDiffer[List, CC]
        .updateByStrPathOrFail(DifferOp.ignore, "each", "dd"),
      List(
        CC(1, "s1", 1),
        CC(2, "s2", 2),
        CC(3, "s2", 2),
      ),
      List(
        CC(1, "s2", 1),
        CC(2, "s1", 2),
        CC(3, "s2", 2),
      ),
      s"""List(
         |  CC(
         |    i: 1,
         |    s: $R"s1"$X -> $G"s2"$X,
         |    dd: $grayIgnoredStr,
         |  ),
         |  CC(
         |    i: 2,
         |    s: $R"s2"$X -> $G"s1"$X,
         |    dd: $grayIgnoredStr,
         |  ),
         |  CC(
         |    i: 3,
         |    s: "s2",
         |    dd: $grayIgnoredStr,
         |  ),
         |)""".stripMargin,
    )
  }

  test("Seq with alternative matchBy should match by the resolved value instead of index") {
    assertConsoleDiffOutput(
      Differ
        .seqDiffer[List, CC]
        .matchBy(_.i),
      List(
        CC(1, "s1", 1),
        CC(2, "s2", 2),
        CC(3, "s2", 3),
      ),
      List(
        CC(2, "s1", 2),
        CC(4, "s2", 4),
        CC(1, "s2", 1),
      ),
      s"""List(
         |  CC(
         |    i: 1,
         |    s: $R"s1"$X -> $G"s2"$X,
         |    dd: 1.0,
         |  ),
         |  CC(
         |    i: 2,
         |    s: $R"s2"$X -> $G"s1"$X,
         |    dd: 2.0,
         |  ),
         |  ${R}CC(
         |    i: 3,
         |    s: "s2",
         |    dd: 3.0,
         |  )${X},
         |  ${G}CC(
         |    i: 4,
         |    s: "s2",
         |    dd: 4.0,
         |  )${X},
         |)""".stripMargin,
    )
  }

  test("Set: isOk == true if two values are equal") {
    assertOkIfValuesEqualProp(Differ.setDiffer[Set, CC])
  }

  test("Set: isOk == false if two values are not equal") {
    assertNotOkIfNotEqualProp(Differ.setDiffer[Set, CC])
  }

  test("Set: isOk always true if differ is marked ignored") {
    assertIsOkIfIgnoredProp(Differ.setDiffer[Set, CC])
  }

  test("Set: match entries base on item identity by default") {
    assertConsoleDiffOutput(
      Differ
        .setDiffer[Set, CC]
        .updateByStrPathOrFail(DifferOp.ignore, "each", "dd"),
      Set(
        CC(1, "s1", 1),
        CC(2, "s2", 2),
        CC(3, "s2", 2),
      ),
      Set(
        CC(1, "s2", 1),
        CC(2, "s1", 2),
        CC(3, "s2", 2),
      ),
      s"""Set(
         |  ${R}CC(
         |    i: 1,
         |    s: "s1",
         |    dd: $justIgnoredStr,
         |  )$X,
         |  ${R}CC(
         |    i: 2,
         |    s: "s2",
         |    dd: $justIgnoredStr,
         |  )$X,
         |  CC(
         |    i: 3,
         |    s: "s2",
         |    dd: $grayIgnoredStr,
         |  ),
         |  ${G}CC(
         |    i: 1,
         |    s: "s2",
         |    dd: $justIgnoredStr,
         |  )${X},
         |  ${G}CC(
         |    i: 2,
         |    s: "s1",
         |    dd: $justIgnoredStr,
         |  )$X,
         |)""".stripMargin,
    )
  }

  test("Set: with alternative matchBy should match by the resolved value instead of index") {
    assertConsoleDiffOutput(
      Differ
        .setDiffer[Set, CC]
        .matchBy(_.i),
      Set(
        CC(1, "s1", 1),
        CC(2, "s2", 2),
        CC(3, "s2", 3),
      ),
      Set(
        CC(2, "s1", 2),
        CC(4, "s2", 4),
        CC(1, "s2", 1),
      ),
      s"""Set(
         |  CC(
         |    i: 1,
         |    s: $R"s1"$X -> $G"s2"$X,
         |    dd: 1.0,
         |  ),
         |  CC(
         |    i: 2,
         |    s: $R"s2"$X -> $G"s1"$X,
         |    dd: 2.0,
         |  ),
         |  ${R}CC(
         |    i: 3,
         |    s: "s2",
         |    dd: 3.0,
         |  )${X},
         |  ${G}CC(
         |    i: 4,
         |    s: "s2",
         |    dd: 4.0,
         |  )${X},
         |)""".stripMargin,
    )
  }

  test("Record: isOk == true if two values are equal") {
    assertOkIfValuesEqualProp(CC.differ)
  }

  test("Record: isOk == false if two values are not equal") {
    assertNotOkIfNotEqualProp(CC.differ)
  }

  test("Record: isOk always true if differ is marked ignored") {
    assertIsOkIfIgnoredProp(CC.differ)
  }

  test("Record: Attempting to update nonexistent field fails") {
    assertEquals(
      CC.differ.updateWith(UpdatePath.of(UpdateStep.DownPath("nonexistent")), DifferOp.ignore),
      Left(
        DifferUpdateError
          .NonExistentField(UpdatePath(Vector(UpdateStep.DownPath("nonexistent")), List.empty), "nonexistent"),
      ),
    )
  }

  test("Record: Trying to update the differ with MatchBy op should fail") {
    assertEquals(
      CC.differ.updateWith(UpdatePath.of(UpdateStep.DownPath("dd")), DifferOp.MatchBy.Index),
      Left(
        DifferUpdateError
          .InvalidDifferOp(UpdatePath(Vector(UpdateStep.DownPath("dd")), List.empty), DifferOp.MatchBy.Index, "record"),
      ),
    )
  }

  private def assertConsoleDiffOutput[A](
    differ: Differ[A],
    actual: A,
    expected: A,
    expectedOutputStr: String,
  ): Unit = {
    val res = differ.diff(actual, expected)
    assertDiffResultRender(res, expectedOutputStr)
  }

  private def assertDiffResultRender(
    res: DiffResult,
    expectedOutputStr: String,
  ): Unit = {
    val actualOutputStr = consoleOutput(res, 0).render

    if (actualOutputStr != expectedOutputStr) {
      println("=== Actual Output === ")
      println(actualOutputStr)
      println("=== Expected Output === ")
      println(expectedOutputStr)
      assertEquals(actualOutputStr, expectedOutputStr)
    } else ()
  }

  private def assertOkIfValuesEqualProp[A: Arbitrary](differ: Differ[A]) = {
    forAll { l: A =>
      val res = differ.diff(l, l)
      assert(res.isOk)
    }
  }

  private def assertNotOkIfNotEqualProp[A: Arbitrary](differ: Differ[A]) = {
    forAll { (l: A, r: A) =>
      (l != r) ==> {
        val res = differ.diff(l, r)
        assert(!res.isOk)
      }
    }
  }

  private def assertIsOkIfIgnoredProp[A: Arbitrary](differ: Differ[A]) = {
    val differIgnored = differ.updateByStrPathOrFail(DifferOp.ignore)
    val differUnignored = differIgnored.updateByStrPathOrFail(DifferOp.unignored)
    forAll { (l: A, r: A) =>
      val ignoredResult = differIgnored.diff(l, r)
      assert(ignoredResult.isOk)
      assertDiffResultRender(
        ignoredResult,
        expectedOutputStr = grayIgnoredStr,
      )
      if (l == r) assert(differUnignored.diff(l, r).isOk)
      else assert(!differUnignored.diff(l, r).isOk)
    }
  }

}

case class CC(i: Int, s: String, dd: Double)

object CC {
  implicit val arb: Arbitrary[CC] = Arbitrary(for {
    i <- Arbitrary.arbitrary[Int]
    s <- Arbitrary.arbitrary[String]
    dd <- Arbitrary.arbitrary[Double]
  } yield CC(i, s, dd))

  implicit val differ: Differ[CC] = DiffGen.derive[CC]
}

sealed trait Foo

object Foo {
  implicit val differ: Differ[Foo] = DiffGen.derive[Foo]
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

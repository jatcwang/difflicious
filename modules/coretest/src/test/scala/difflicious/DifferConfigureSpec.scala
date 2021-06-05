package difflicious

import difflicious.testutils._
import difflicious.testtypes._
import difflicious.implicits._
import difflicious.utils.Eachable2

// Tests for configuring a Differ
class DifferConfigureSpec extends munit.FunSuite {

  test("configure path's subType call errors when super type isn't sealed") {
    val compileError = compileErrors("Differ[List[OpenSuperType]].ignoreAt(_.each.subType[OpenSub])")
    val firstLine = compileError.linesIterator.toList.drop(1).head
    assertEquals(firstLine, "Specified subtype is not a known direct subtype of trait OpenSuperType.")
  }

  test("configure path's subType handles multi-level hierarchies") {
    assertConsoleDiffOutput(
      Differ[List[Sealed]].ignoreAt(_.each.subType[SubSealed.SubSub1].d),
      List(
        SubSealed.SubSub1(1.0),
      ),
      List(
        SubSealed.SubSub1(2.0),
      ),
      s"""List(
        |  SubSub1(
        |    d: $grayIgnoredStr,
        |  ),
        |)""".stripMargin,
    )
  }

  test("configure path allows 'each' to resolve underlying differ in a Map") {
    assertConsoleDiffOutput(
      Differ[Map[String, CC]].ignoreAt(_.each.dd),
      Map(
        "a" -> CC(1, "s", 1.0),
      ),
      Map(
        "a" -> CC(1, "s", 2.0),
      ),
      s"""Map(
        |  "a" -> CC(
        |      i: 1,
        |      s: "s",
        |      dd: $I[IGNORED]$X,
        |    ),
        |)""".stripMargin,
    )
  }
  test("configure path allows 'each' to resolve underlying differ in a Seq") {
    assertConsoleDiffOutput(
      Differ[List[CC]].ignoreAt(_.each.dd),
      List(
        CC(1, "s", 1.0),
      ),
      List(
        CC(1, "s", 2.0),
      ),
      s"""List(
       |  CC(
       |    i: 1,
       |    s: "s",
       |    dd: $I[IGNORED]$X,
       |  ),
       |)""".stripMargin,
    )
  }

  test("configure path allows 'each' to resolve underlying differ in a Set") {
    assertConsoleDiffOutput(
      Differ[Set[CC]].pairBy(_.i).ignoreAt(_.each.dd),
      Set(
        CC(1, "s", 1.0),
      ),
      Set(
        CC(1, "s", 2.0),
      ),
      s"""Set(
       |  CC(
       |    i: 1,
       |    s: "s",
       |    dd: $I[IGNORED]$X,
       |  ),
       |)""".stripMargin,
    )
  }

  test("configure path can handle escaped sub-type and field names") {
    assertConsoleDiffOutput(
      Differ[List[Sealed]].ignoreAt(_.each.subType[`Weird@Sub`].`weird@Field`),
      List(
        `Weird@Sub`(1, "a"),
      ),
      List(
        `Weird@Sub`(1, "x"),
      ),
      s"""List(
         |  Weird@Sub(
         |    i: 1,
         |    weird@Field: $I[IGNORED]$X,
         |  ),
         |)""".stripMargin,
    )
  }

  test("pairBy works with Seq") {
    assertConsoleDiffOutput(
      Differ[HasASeq[CC]].configure(_.seq)(_.pairBy(_.i)),
      HasASeq(
        Seq(
          CC(1, "s", 1.0),
          CC(2, "s", 2.0),
        ),
      ),
      HasASeq(
        Seq(
          CC(2, "s", 4.0),
          CC(1, "s", 2.0),
        ),
      ),
      s"""HasASeq(
         |  seq: Seq(
         |    CC(
         |      i: 1,
         |      s: "s",
         |      dd: ${R}1.0$X -> ${G}2.0$X,
         |    ),
         |    CC(
         |      i: 2,
         |      s: "s",
         |      dd: ${R}2.0$X -> ${G}4.0$X,
         |    ),
         |  ),
         |)""".stripMargin,
    )
  }

  test("pairBy works with Set") {
    assertConsoleDiffOutput(
      Differ[Map[String, Set[CC]]].configure(x => x.each)(_.pairBy(_.i)),
      Map(
        "a" -> Set(
          CC(1, "s", 1.0),
          CC(2, "s", 2.0),
        ),
      ),
      Map(
        "a" -> Set(
          CC(2, "s", 4.0),
          CC(1, "s", 2.0),
        ),
      ),
      s"""Map(
         |  "a" -> Set(
         |      CC(
         |        i: 1,
         |        s: "s",
         |        dd: ${R}1.0$X -> ${G}2.0$X,
         |      ),
         |      CC(
         |        i: 2,
         |        s: "s",
         |        dd: ${R}2.0$X -> ${G}4.0$X,
         |      ),
         |    ),
         |)""".stripMargin,
    )
  }

  test("'replace' for MapDiffer replaces value differ when step is 'each'") {
    implicit val ignoredDiffer: Differ[CC] = CC.differ.ignore
    val differ: Differ[Map[String, CC]] = Differ[Map[String, CC]]
    implicitly[Eachable2[Map]](mapEachable2)
    toEachable2Ops(Map(1 -> 1)).each
    val newDiffer = differ.replace[CC](_.each)(CC.differ)

    assertConsoleDiffOutput(
      differ,
      Map(
        "a" -> CC(1, "s", 1.0),
      ),
      Map(
        "b" -> CC(2, "s", 4.0),
      ),
      s"""Map(
         |  "a" -> Set(
         |      CC(
         |        i: 1,
         |        s: "s",
         |        dd: ${R}1.0$X -> ${G}2.0$X,
         |      ),
         |      CC(
         |        i: 2,
         |        s: "s",
         |        dd: ${R}2.0$X -> ${G}4.0$X,
         |      ),
         |    ),
         |)""".stripMargin,
    )
  }

  test("'configure' for MapDiffer transforms value differ when step is 'each'") {
    ???
  }

  test("'replace' for MapDiffer fails if step isn't 'each'") {
    ???
  }

  test("'replace' for MapDiffer fails if type tag mismatches") {
    ???
  }

  test("'replace' for SeqDiffer replaces ite differ when step is 'each'") {
    ???
  }

  test("'replace' for SeqDiffer fails if type tag mismatches") {
    ???
  }

  test("'replace' for SeqDiffer fails if step isn't 'each'") {
    ???
  }

  test("'replace' for SetDiffer replaces ite differ when step is 'each'") {
    ???
  }

  test("'replace' for SetDiffer fails if type tag mismatches") {
    ???
  }

  test("'replace' for SeqDiffer fails if step isn't 'each'") {
    ???
  }
}

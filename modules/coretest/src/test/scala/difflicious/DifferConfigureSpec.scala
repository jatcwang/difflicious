package difflicious

import difflicious.testutils._
import difflicious.testtypes._
import difflicious.implicits._

class DifferConfigureSpec extends munit.FunSuite {

  test("configure path's subType call errors when super type isn't sealed") {
    val compileError = compileErrors("Differ[List[OpenSuperType]].configureIgnore(_.each.subType[OpenSub])")
    val firstLine = compileError.linesIterator.toList.drop(1).head
    assertEquals(firstLine, "Specified subtype is not a known direct subtype of trait OpenSuperType.")
  }

  test("configure path allows 'each' to resolve underlying differ in a Map") {
    assertConsoleDiffOutput(
      Differ[Map[String, CC]].configureIgnore(_.each.dd),
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
      Differ[List[CC]].configureIgnore(_.each.dd),
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
      Differ.setDiffer[Set, CC].pairBy(_.i).configureIgnore(_.each.dd),
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

  test("configurePairBy works with Seq") {
    assertConsoleDiffOutput(
      Differ[HasASeq[CC]].configurePairBy(_.seq)(_.i),
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

  test("configurePairBy works with Set") {
    assertConsoleDiffOutput(
      Differ[Map[String, Set[CC]]].configurePairBy(_.each)(_.i),
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

  test("configure path can handle escaped sub-type and field names") {
    assertConsoleDiffOutput(
      Differ[List[Sealed]].configureIgnore(_.each.subType[`Weird@Sub`].`weird@Field`),
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

}

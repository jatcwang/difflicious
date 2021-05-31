package difflicious

import cats.data.Ior
import difflicious.DifferUpdateError.InvalidDifferOp
import munit.ScalaCheckSuite
import difflicious.testutils._
import difflicious.testtypes._
import izumi.reflect.macrortti.LTag
import difflicious.internal.EitherGetSyntax._

import scala.collection.immutable.HashSet

class DifferSpec extends ScalaCheckSuite {
  test("NumericDiffer: configure fails if path is not terminal") {
    assertEquals(
      Differ[Int].configureRaw(ConfigurePath.of("nono"), ConfigureOp.ignore),
      Left(DifferUpdateError.PathTooLong(ConfigurePath(Vector("nono"), List.empty))),
    )
  }

  // FIXME: need to check nested sealed traits, where the child sealed trait has a custom differ defined

  test("NumericDiffer: configure fails if differ op is SetIgnore") {
    assertEquals(
      Differ[Int].configureRaw(ConfigurePath.current, ConfigureOp.PairBy.Index),
      Left(
        DifferUpdateError
          .InvalidDifferOp(ConfigurePath(Vector.empty, List.empty), ConfigureOp.PairBy.Index, "NumericDiffer"),
      ),
    )
  }

  test("EqualsDiffer: return Both/ObtainedOnly/ExpectedOnly depending on whether both sides are present in diff") {
    assertConsoleDiffOutput(
      Differ.mapDiffer[Map, String, EqClass],
      Map(
        "a" -> EqClass(1),
        "b" -> EqClass(2),
      ),
      Map(
        "a" -> EqClass(1),
        "c" -> EqClass(3),
      ),
      s"""Map(
         |  $R"b"$X -> EqClass(2),
         |  "a" -> EqClass(1),
         |  $G"c"$X -> EqClass(3),
         |)""".stripMargin,
    )
  }

  test("EqualsDiffer: ObtainedOnly#isOk should always be false") {
    assertEquals(EqClass.differ.diff(Ior.Left(EqClass(1))).isOk, false)
  }

  test("EqualsDiffer: ObtainedOnly#isOk should always be false") {
    assertEquals(EqClass.differ.diff(Ior.Right(EqClass(1))).isOk, false)
  }

  test("EqualsDiffer: configure fails if path is not terminal") {
    assertEquals(
      EqClass.differ.configureRaw(ConfigurePath.of("asdf"), ConfigureOp.ignore),
      Left(DifferUpdateError.PathTooLong(ConfigurePath(Vector("asdf"), List.empty))),
    )
  }

  test("EqualsDiffer: configure fails if op is not setting ignore") {
    assertEquals(
      EqClass.differ.configureRaw(ConfigurePath.current, ConfigureOp.PairBy.Index),
      Left(InvalidDifferOp(ConfigurePath(Vector.empty, List.empty), ConfigureOp.PairBy.Index, "EqualsDiffer")),
    )
  }

  test("EqualsDiffer: isOk == true if two values are equal") {
    assertOkIfValuesEqualProp(EqClass.differ)
  }

  test("EqualsDiffer: isOk == false if two values are NOT equal") {
    assertNotOkIfNotEqualProp(EqClass.differ)
  }

  test("EqualsDiffer: isOk always true if differ is marked ignored") {
    assertIsOkIfIgnoredProp(EqClass.differ)
  }

  test("Tuple2: isOk == true if two values are equal") {
    assertOkIfValuesEqualProp(Differ[(String, CC)])
  }

  test("Tuple2: isOk == false if two values are NOT equal") {
    assertNotOkIfNotEqualProp(Differ[(String, CC)])
  }

  test("Tuple2: isOk always true if differ is marked ignored") {
    assertIsOkIfIgnoredProp(Differ[(CC, Int)])
  }

  test("Tuple3: isOk == true if two values are equal") {
    assertOkIfValuesEqualProp(Differ[Tuple3[String, CC, Int]])
  }

  test("Tuple3: isOk == false if two values are NOT equal") {
    assertNotOkIfNotEqualProp(Differ[Tuple3[String, CC, Int]])
  }

  test("Tuple3: isOk always true if differ is marked ignored") {
    assertIsOkIfIgnoredProp(Differ[Tuple3[String, CC, Int]])
  }

  test("Tuple3: compared like a record") {
    assertConsoleDiffOutput(
      Differ[(String, Int, CC)],
      Tuple3("asdf", 1, CC(1, "1", 1.0)),
      Tuple3("s", 2, CC(2, "2", 3.0)),
      s"""Tuple3(
         |  _1: $R"asdf"$X -> $G"s"$X,
         |  _2: ${R}1$X -> ${G}2$X,
         |  _3: CC(
         |    i: ${R}1$X -> ${G}2$X,
         |    s: ${R}"1"$X -> ${G}"2"$X,
         |    dd: ${R}1.0$X -> ${G}3.0$X,
         |  ),
         |)""".stripMargin,
    )
  }

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
      Differ.mapDiffer[Map, MapKey, CC].configureRaw(ConfigurePath.of("each", "i"), ConfigureOp.ignore).unsafeGet,
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
         |  ${R}MapKey(2,sa)$X -> ${R}CC(
         |      i: $justIgnoredStr,
         |      s: "s2",
         |      dd: 2.0,
         |    )$X,
         |  MapKey(1,s) -> CC(
         |      i: $grayIgnoredStr,
         |      s: "s1",
         |      dd: 1.0,
         |    ),
         |  MapKey(4,diff) -> CC(
         |      i: $grayIgnoredStr,
         |      s: $R"s4"$X -> $G"sx"$X,
         |      dd: 1.0,
         |    ),
         |  ${G}MapKey(3,se)$X -> ${G}CC(
         |      i: $justIgnoredStr,
         |      s: "s3",
         |      dd: 2.0,
         |    )${X},
         |)""".stripMargin,
    )
  }

  test("Map: When only 'obtained' is provided when diffing") {
    assertConsoleDiffOutput(
      Differ[List[Map[MapKey, CC]]],
      List(
        Map(
          MapKey(1, "s") -> CC(1, "s1", 1),
        ),
      ),
      List.empty,
      s"""List(
         |  ${R}Map(
         |    MapKey(1,s) -> CC(
         |        i: 1,
         |        s: "s1",
         |        dd: 1.0,
         |      ),
         |  )$X,
         |)""".stripMargin,
    )
  }

  test("Map: When only 'expected' is provided when diffing") {
    assertConsoleDiffOutput(
      Differ[List[Map[MapKey, CC]]],
      List.empty,
      List(
        Map(
          MapKey(1, "s") -> CC(1, "s1", 1),
        ),
      ),
      s"""List(
         |  ${G}Map(
         |    MapKey(1,s) -> CC(
         |        i: 1,
         |        s: "s1",
         |        dd: 1.0,
         |      ),
         |  )$X,
         |)""".stripMargin,
    )
  }

  test("Map: Allow updating value differs using the path 'each'") {
    val differ = Differ[Map[String, List[CC]]]
      .configureRaw(ConfigurePath.of("each"), ConfigureOp.PairBy.func((s: CC) => s.i))
      .unsafeGet
    val diffResult = differ.diff(
      Map(
        "a" -> List(
          CC(1, "1", 1),
          CC(2, "2", 2),
        ),
      ),
      Map(
        "a" -> List(
          CC(2, "2", 2),
          CC(1, "1", 1),
        ),
      ),
    )
    assert(diffResult.isOk)
  }

  test("Map: configureRaw fails if field name isn't 'each'") {
    assertEquals(
      Differ[Map[String, String]].configureRaw(ConfigurePath.of("nono"), ConfigureOp.ignore),
      Left(DifferUpdateError.NonExistentField(ConfigurePath(Vector("nono"), List.empty), "nono")),
    )
  }

  test("Map: configureRaw fails if operation isn't ignore") {
    assertEquals(
      Differ[Map[String, String]].configureRaw(ConfigurePath.current, ConfigureOp.PairBy.Index),
      Left(InvalidDifferOp(ConfigurePath(Vector.empty, List.empty), ConfigureOp.PairBy.Index, "MapDiffer")),
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

  test("Seq: match entries base on item index by default") {
    assertConsoleDiffOutput(
      Differ
        .seqDiffer[List, CC]
        .configureRaw(ConfigurePath.of("each", "dd"), ConfigureOp.ignore)
        .unsafeGet,
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

  test("Seq: with alternative pairBy should match by the resolved value instead of index") {
    assertConsoleDiffOutput(
      Differ
        .seqDiffer[List, CC]
        .pairBy(_.i),
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

  test("Seq: Can set pairBy to match by index again") {
    assertConsoleDiffOutput(
      Differ
        .seqDiffer[List, Int]
        .pairBy(identity)
        .pairByIndex,
      List(
        1,
        2,
      ),
      List(
        2,
        1,
      ),
      s"""List(
         |  ${R}1$X -> ${G}2$X,
         |  ${R}2$X -> ${G}1$X,
         |)""".stripMargin,
    )
  }

  test("Seq: Only 'obtained' is provided when diffing") {
    assertConsoleDiffOutput(
      Differ[Map[String, List[Int]]],
      Map(
        "a" -> List(1, 2, 3),
      ),
      Map.empty[String, List[Int]],
      s"""Map(
         |  $R"a"$X -> ${R}List(
         |      1,
         |      2,
         |      3,
         |    )$X,
         |)""".stripMargin,
    )
  }

  test("Seq: Only 'expected' is provided when diffing") {
    assertConsoleDiffOutput(
      Differ[Map[String, List[Int]]],
      Map.empty[String, List[Int]],
      Map(
        "a" -> List(1, 2, 3),
      ),
      s"""Map(
         |  $G"a"$X -> ${G}List(
         |      1,
         |      2,
         |      3,
         |    )$X,
         |)""".stripMargin,
    )
  }

  test("Seq: Allow modifying element differs using the path 'each'") {
    val differ = Differ[List[CC]]
      .configureRaw(ConfigurePath.of("each", "i"), ConfigureOp.ignore)
      .unsafeGet
    val diffResult = differ.diff(
      List(
        CC(
          1,
          "2",
          3.0,
        ),
      ),
      List(
        CC(
          2,
          "2",
          3.0,
        ),
      ),
    )
    assert(diffResult.isOk)
  }

  test("Seq: configureRaw fails if field name isn't 'each'") {
    assertEquals(
      Differ[Seq[String]].configureRaw(ConfigurePath.of("nono"), ConfigureOp.ignore),
      Left(DifferUpdateError.NonExistentField(ConfigurePath(Vector("nono"), List.empty), "nono")),
    )
  }

  test("Set: match by where the item tag does not match the expected should fail") {
    assertEquals(
      Differ.seqDiffer[List, CC].configureRaw(ConfigurePath.current, ConfigureOp.PairBy.func[Int, Double](_.toDouble)),
      Left(
        DifferUpdateError
          .PairByTypeMismatch(ConfigurePath(Vector.empty, List.empty), LTag[Int].tag, LTag[CC].tag),
      ),
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
        .configureRaw(ConfigurePath.of("each", "dd"), ConfigureOp.ignore)
        .unsafeGet,
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

  test("Set: When only 'obtained' is provided when diffing") {
    assertConsoleDiffOutput(
      Differ[List[Set[Int]]],
      List(
        Set(1, 2),
      ),
      List.empty,
      s"""List(
         |  ${R}Set(
         |    1,
         |    2,
         |  )$X,
         |)""".stripMargin,
    )
  }

  test("Set: When only 'expected' is provided when diffing") {
    assertConsoleDiffOutput(
      Differ[List[Set[Int]]],
      List.empty,
      List(
        Set(1, 2),
      ),
      s"""List(
         |  ${G}Set(
         |    1,
         |    2,
         |  )$X,
         |)""".stripMargin,
    )
  }

  test("Set: Allow modifying element differs using the path 'each'") {
    val differ = Differ[HashSet[CC]]
      .configureRaw(ConfigurePath.of("each", "i"), ConfigureOp.ignore)
      .flatMap(
        _.configureRaw(ConfigurePath.current, ConfigureOp.PairBy.func((c: CC) => c.s)),
      )
      .unsafeGet
    val diffResult = differ.diff(
      HashSet(
        CC(
          1,
          "2",
          3.0,
        ),
      ),
      HashSet(
        CC(
          2,
          "2",
          3.0,
        ),
      ),
    )
    assert(diffResult.isOk)
  }

  test("Set: Update fails if field name isn't 'each'") {
    assertEquals(
      Differ[Set[String]].configureRaw(ConfigurePath.of("nono"), ConfigureOp.ignore),
      Left(DifferUpdateError.NonExistentField(ConfigurePath(Vector("nono"), List.empty), "nono")),
    )
  }

  test("Set: match by where the item tag does not match the expected should fail") {
    assertEquals(
      Differ.setDiffer[Set, CC].configureRaw(ConfigurePath.current, ConfigureOp.PairBy.func[Int, Double](_.toDouble)),
      Left(
        DifferUpdateError
          .PairByTypeMismatch(ConfigurePath(Vector.empty, List.empty), LTag[Int].tag, LTag[CC].tag),
      ),
    )
  }

  test("Set: errors when trying to update the set to match by index (since Set has no inherent order)") {
    assertEquals(
      Differ.setDiffer[Set, CC].configureRaw(ConfigurePath.current, ConfigureOp.PairBy.Index),
      Left(
        DifferUpdateError
          .InvalidDifferOp(ConfigurePath(Vector.empty, List.empty), ConfigureOp.PairBy.Index, "Set"),
      ),
    )
  }

  test("Set: with alternative pairBy should match by the resolved value instead of index") {
    assertConsoleDiffOutput(
      Differ
        .setDiffer[Set, CC]
        .pairBy(_.i),
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
      CC.differ.configureRaw(ConfigurePath.of("nonexistent"), ConfigureOp.ignore),
      Left(
        DifferUpdateError
          .NonExistentField(ConfigurePath(Vector("nonexistent"), List.empty), "nonexistent"),
      ),
    )
  }

  test("Record: Trying to update the differ with PairBy op should fail") {
    assertEquals(
      CC.differ.configureRaw(ConfigurePath.current, ConfigureOp.PairBy.Index),
      Left(
        DifferUpdateError
          .InvalidDifferOp(
            ConfigurePath(Vector.empty, List.empty),
            ConfigureOp.PairBy.Index,
            "record",
          ),
      ),
    )
  }

  test("Record: ignoreFieldByNameOrFail succeeds if field exists") {
    assertEquals(
      CC.differ.configureRaw(ConfigurePath.current, ConfigureOp.PairBy.Index),
      Left(
        DifferUpdateError
          .InvalidDifferOp(
            ConfigurePath(Vector.empty, List.empty),
            ConfigureOp.PairBy.Index,
            "record",
          ),
      ),
    )
  }

  test("Sealed trait: should display obtained and expected types when mismatch") {
    assertConsoleDiffOutput(
      Sealed.differ,
      Sub1(1),
      SubSub1(1),
      s"""${R}Sub1$X != ${G}SubSub1$X
        |${R}=== Obtained ===
        |Sub1(
        |  i: 1,
        |)$X
        |$G=== Expected ===
        |SubSub1(
        |  d: 1.0,
        |)$X""".stripMargin,
    )
  }

  test("Sealed trait: should display obtained and expected types when mismatch") {
    assertConsoleDiffOutput(
      Sealed.differ,
      Sub1(1),
      SubSub1(1),
      s"""${R}Sub1$X != ${G}SubSub1$X
         |${R}=== Obtained ===
         |Sub1(
         |  i: 1,
         |)$X
         |$G=== Expected ===
         |SubSub1(
         |  d: 1.0,
         |)$X""".stripMargin,
    )
  }

  test("Sealed trait: isOk == true if two values are equal") {
    assertOkIfValuesEqualProp(Sealed.differ)
  }

  test("Sealed trait: isOk == false if two values are NOT equal") {
    assertNotOkIfNotEqualProp(Sealed.differ)
  }

  test("Sealed trait: isOk always true if differ is marked ignored") {
    assertIsOkIfIgnoredProp(Sealed.differ)
  }

  test("Sealed trait: When only 'obtained' is provided when diffing") {
    assertConsoleDiffOutput(
      Differ[List[Sealed]],
      List(Sub1(1)),
      List.empty[Sealed],
      s"""List(
         |  ${R}Sub1(
         |    i: 1,
         |  )$X,
         |)""".stripMargin,
    )
  }

  test("Sealed trait: When only 'expected' is provided when diffing") {
    assertConsoleDiffOutput(
      Differ[List[Sealed]],
      List.empty[Sealed],
      List(Sub1(1)),
      s"""List(
         |  ${G}Sub1(
         |    i: 1,
         |  )$X,
         |)""".stripMargin,
    )
  }

  test("Sealed trait: update subtype differs by specifying the subtype name in the path") {
    val differ = Differ[Sealed]
      .configureRaw(
        ConfigurePath
          .of("SubSub2", "list"),
        ConfigureOp.PairBy.func((c: CC) => c.i),
      )
      .unsafeGet

    val diffResult = differ.diff(
      SubSub2(
        List(
          CC(1, "1", 1),
          CC(2, "2", 2),
        ),
      ),
      SubSub2(
        List(
          CC(2, "2", 2),
          CC(1, "1", 1),
        ),
      ),
    )

    assert(diffResult.isOk)

    assertConsoleDiffOutput(
      differ,
      SubSub2(
        List(
          CC(1, "1", 1),
          CC(2, "2", 2),
        ),
      ),
      SubSub2(
        List(
          CC(2, "2", 2),
          CC(1, "2", 1),
        ),
      ),
      s"""SubSub2(
        |  list: List(
        |    CC(
        |      i: 1,
        |      s: $R"1"$X -> $G"2"$X,
        |      dd: 1.0,
        |    ),
        |    CC(
        |      i: 2,
        |      s: "2",
        |      dd: 2.0,
        |    ),
        |  ),
        |)""".stripMargin,
    )
  }

  test("Sealed trait: error if trying to update with an invalid subtype name as path") {
    assertEquals(
      Differ[Sealed]
        .configureRaw(
          ConfigurePath
            .of("nope", "list"),
          ConfigureOp.PairBy.Index,
        ),
      Left(
        DifferUpdateError.UnrecognizedSubType(
          ConfigurePath(Vector("nope"), List("list")),
          Vector(
            "difflicious.testtypes.Sub1",
            "difflicious.testtypes.SubSub1",
            "difflicious.testtypes.SubSub2",
          ),
        ),
      ),
    )
  }

  // FIXME: reword desc
  test("Sealed trait: error if trying to update with an unsupported differ update op") {
    assertEquals(
      Differ[Sealed]
        .configureRaw(
          ConfigurePath.current,
          ConfigureOp.PairBy.Index,
        ),
      Left(
        DifferUpdateError.InvalidDifferOp(
          ConfigurePath.current,
          ConfigureOp.PairBy.Index,
          "sealed trait",
        ),
      ),
    )
  }

}

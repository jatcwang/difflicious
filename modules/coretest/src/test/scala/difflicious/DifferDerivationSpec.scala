package difflicioustest

import difflicious.Differ
import difflicious.testutils.*
import difflicious.testtypes.*

class DifferDerivationSpec extends munit.FunSuite {

  test("derivedDeep recursively derives missing case class field instances") {
    val subject: Differ[SimpleCaseClassSubject] = Differ.derivedDeep[SimpleCaseClassSubject]
    val result = subject.diff(
      SimpleCaseClassSubject(SimpleCaseClass1("a"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
      SimpleCaseClassSubject(SimpleCaseClass1("b"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
    )
    assertEquals(result.isOk, false)
  }

  test("derivedDeep recursively derives missing item instances for collection fields") {
    val subject: Differ[SimpleCaseClassListSubject] = Differ.derivedDeep[SimpleCaseClassListSubject]
    assertConsoleDiffOutput(
      subject,
      SimpleCaseClassListSubject(List(SimpleCaseClass1("a"))),
      SimpleCaseClassListSubject(List(SimpleCaseClass1("b"))),
      s"""SimpleCaseClassListSubject(
         |  values: List(
         |    SimpleCaseClass1(
         |      value: $R"a"$X -> $G"b"$X
         |    )
         |  )
         |)""".stripMargin,
    )
  }

  test("derivedDeep recursively derives missing generic case class field instances") {
    val subject: Differ[SimpleGenericCaseClassSubject] = Differ.derivedDeep[SimpleGenericCaseClassSubject]
    assertConsoleDiffOutput(
      subject,
      SimpleGenericCaseClassSubject(SimpleGenericCaseClass(SimpleCaseClass1("a"))),
      SimpleGenericCaseClassSubject(SimpleGenericCaseClass(SimpleCaseClass1("b"))),
      s"""SimpleGenericCaseClassSubject(
         |  value: SimpleGenericCaseClass(
         |    value: SimpleCaseClass1(
         |      value: $R"a"$X -> $G"b"$X
         |    )
         |  )
         |)""".stripMargin,
    )
  }

  test("derived works for generic case classes with generic field instances") {
    val subject: Differ[GenericFactory[Int]] = Differ[GenericFactory[Int]]

    assertConsoleDiffOutput(
      subject,
      GenericFactory(List(GenericBox(List(1)))),
      GenericFactory(List(GenericBox(List(2)))),
      s"""GenericFactory(
         |  boxes: List(
         |    GenericBox(
         |      content: List(
         |        ${R}1$X -> ${G}2$X
         |      )
         |    )
         |  )
         |)""".stripMargin,
    )
  }

  test("derivedDeep works for generic case classes") {
    val subject: Differ[GenericFactory[SimpleCaseClass1]] =
      Differ.derivedDeep[GenericFactory[SimpleCaseClass1]]

    assertConsoleDiffOutput(
      subject,
      GenericFactory(List(GenericBox(List(SimpleCaseClass1("a"))))),
      GenericFactory(List(GenericBox(List(SimpleCaseClass1("b"))))),
      s"""GenericFactory(
         |  boxes: List(
         |    GenericBox(
         |      content: List(
         |        SimpleCaseClass1(
         |          value: $R"a"$X -> $G"b"$X
         |        )
         |      )
         |    )
         |  )
         |)""".stripMargin,
    )
  }

  test("derived works for recursive data structures") {
    val subject: Differ[RecursiveDerivedNode] = Differ.derived[RecursiveDerivedNode]

    assertConsoleDiffOutput(
      subject,
      RecursiveDerivedNode(
        "root",
        List(
          RecursiveDerivedNode("same", Nil),
          RecursiveDerivedNode("left", List(RecursiveDerivedNode("left-leaf", Nil))),
        ),
      ),
      RecursiveDerivedNode(
        "root",
        List(
          RecursiveDerivedNode("same", Nil),
          RecursiveDerivedNode("right", List(RecursiveDerivedNode("right-leaf", Nil))),
        ),
      ),
      s"""RecursiveDerivedNode(
         |  value: "root",
         |  children: List(
         |    RecursiveDerivedNode(
         |      value: "same",
         |      children: List(
         |      )
         |    ),
         |    RecursiveDerivedNode(
         |      value: $R"left"$X -> $G"right"$X,
         |      children: List(
         |        RecursiveDerivedNode(
         |          value: $R"left-leaf"$X -> $G"right-leaf"$X,
         |          children: List(
         |          )
         |        )
         |      )
         |    )
         |  )
         |)""".stripMargin,
    )
  }

  test("derived works for recursive data structures with custom SeqLike fields") {
    val subject: Differ[RecursiveNodeWithCustomList] = Differ.derived[RecursiveNodeWithCustomList]

    assertRecursiveCustomListDiffer(subject)
  }

  test("derivedDeep works for recursive data structures") {
    val subject: Differ[RecursiveDerivedDeepNode] = Differ.derivedDeep[RecursiveDerivedDeepNode]

    assertConsoleDiffOutput(
      subject,
      RecursiveDerivedDeepNode(
        "root",
        List(
          RecursiveDerivedDeepNode("same", Nil),
          RecursiveDerivedDeepNode("left", List(RecursiveDerivedDeepNode("left-leaf", Nil))),
        ),
      ),
      RecursiveDerivedDeepNode(
        "root",
        List(
          RecursiveDerivedDeepNode("same", Nil),
          RecursiveDerivedDeepNode("right", List(RecursiveDerivedDeepNode("right-leaf", Nil))),
        ),
      ),
      s"""RecursiveDerivedDeepNode(
         |  value: "root",
         |  children: List(
         |    RecursiveDerivedDeepNode(
         |      value: "same",
         |      children: List(
         |      )
         |    ),
         |    RecursiveDerivedDeepNode(
         |      value: $R"left"$X -> $G"right"$X,
         |      children: List(
         |        RecursiveDerivedDeepNode(
         |          value: $R"left-leaf"$X -> $G"right-leaf"$X,
         |          children: List(
         |          )
         |        )
         |      )
         |    )
         |  )
         |)""".stripMargin,
    )
  }

  test("derivedDeep works for recursive data structures with custom SeqLike fields") {
    val subject: Differ[RecursiveNodeWithCustomList] = Differ.derivedDeep[RecursiveNodeWithCustomList]

    assertRecursiveCustomListDiffer(subject)
  }

  test("derivedDeep uses manually defined instances for fields") {
    implicit val d: Differ[SimpleCaseClass1] =
      Differ.derived[SimpleCaseClass1].ignoreAt(_.value)

    val result = Differ
      .derivedDeep[SimpleCaseClassSubject]
      .diff(
        SimpleCaseClassSubject(SimpleCaseClass1("a"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
        SimpleCaseClassSubject(SimpleCaseClass1("b"), SimpleCaseClass2("a"), SimpleCaseClass3("a")),
      )
    assertEquals(result.isOk, true)
  }

  test("derivedDeep uses manually defined instances for collection items") {
    implicit val d: Differ[SimpleCaseClass1] =
      Differ.derived[SimpleCaseClass1].ignoreAt(_.value)

    val result = Differ
      .derivedDeep[SimpleCaseClassListSubject]
      .diff(
        SimpleCaseClassListSubject(List(SimpleCaseClass1("a"))),
        SimpleCaseClassListSubject(List(SimpleCaseClass1("b"))),
      )
    assertEquals(result.isOk, true)
  }

  test("derivedDeep derives sealed child field instances and uses manually defined ones") {
    implicit val d: Differ[SimpleCaseClass1] =
      Differ.derived[SimpleCaseClass1].ignoreAt(_.value)

    val subject: Differ[DeepSealedSubject] = Differ.derivedDeep[DeepSealedSubject]

    val manualFieldResult = subject.diff(
      DeepSealedSubject(DeepSealed.UsesManual(SimpleCaseClass1("a"))),
      DeepSealedSubject(DeepSealed.UsesManual(SimpleCaseClass1("b"))),
    )
    assertEquals(manualFieldResult.isOk, true)

    val derivedFieldResult = subject.diff(
      DeepSealedSubject(DeepSealed.UsesDerived(SimpleCaseClass2("a"))),
      DeepSealedSubject(DeepSealed.UsesDerived(SimpleCaseClass2("b"))),
    )
    assertEquals(derivedFieldResult.isOk, false)
  }

  test("derived works for sealed traits with generic type parameters") {
    implicit val elementDiffer: Differ[SimpleCaseClass1] =
      Differ.derived[SimpleCaseClass1]

    val subject: Differ[GenericSealed[SimpleCaseClass1]] =
      Differ.derived[GenericSealed[SimpleCaseClass1]]

    assertConsoleDiffOutput(
      subject,
      GenericSealed.Many(List(SimpleCaseClass1("a"))),
      GenericSealed.Many(List(SimpleCaseClass1("b"))),
      s"""Many(
         |  values: List(
         |    SimpleCaseClass1(
         |      value: $R"a"$X -> $G"b"$X
         |    )
         |  )
         |)""".stripMargin,
    )
  }

  test("derivedDeep derives sealed traits with generic type parameters") {
    val subject: Differ[GenericSealedSubject[SimpleCaseClass1]] =
      Differ.derivedDeep[GenericSealedSubject[SimpleCaseClass1]]

    assertConsoleDiffOutput(
      subject,
      GenericSealedSubject(GenericSealed.Single(SimpleCaseClass1("a"))),
      GenericSealedSubject(GenericSealed.Single(SimpleCaseClass1("b"))),
      s"""GenericSealedSubject(
         |  value: Single(
         |    value: SimpleCaseClass1(
         |      value: $R"a"$X -> $G"b"$X
         |    )
         |  )
         |)""".stripMargin,
    )
  }

  test("derived works for multi level sealed traits") {
    val subject: Differ[MultiLevelSealed] = Differ.derived[MultiLevelSealed]

    assertConsoleDiffOutput(
      subject,
      MultiLevelSealed.Nested.NestedLeaf("a"),
      MultiLevelSealed.Nested.NestedLeaf("b"),
      s"""NestedLeaf(
         |  value: $R"a"$X -> $G"b"$X
         |)""".stripMargin,
    )

    assertEquals(
      subject
        .diff(
          MultiLevelSealed.Nested.NestedLeaf("a"),
          MultiLevelSealed.Nested.NestedOther(1),
        )
        .isOk,
      false,
    )
  }

  test("derived reuses existing subtype instances for multi level sealed traits") {
    implicit val nestedLeafDiffer: Differ[MultiLevelSealed.Nested.NestedLeaf] =
      Differ.derived[MultiLevelSealed.Nested.NestedLeaf].ignoreAt(_.value)

    val subject: Differ[MultiLevelSealed] = Differ.derived[MultiLevelSealed]

    assertConsoleDiffOutput(
      subject,
      MultiLevelSealed.Nested.NestedLeaf("a"),
      MultiLevelSealed.Nested.NestedLeaf("b"),
      s"""NestedLeaf(
         |  value: $grayIgnoredStr
         |)""".stripMargin,
    )
  }

  test("derivedDeep works for multi level sealed traits") {
    val subject: Differ[MultiLevelSealed] = Differ.derivedDeep[MultiLevelSealed]

    assertConsoleDiffOutput(
      subject,
      MultiLevelSealed.Nested.NestedLeaf("a"),
      MultiLevelSealed.Nested.NestedLeaf("b"),
      s"""NestedLeaf(
         |  value: $R"a"$X -> $G"b"$X
         |)""".stripMargin,
    )

    assertEquals(
      subject
        .diff(
          MultiLevelSealed.Nested.NestedLeaf("a"),
          MultiLevelSealed.Nested.NestedOther(1),
        )
        .isOk,
      false,
    )
  }

  test("derivedDeep reuses existing subtype instances for multi level sealed traits") {
    implicit val nestedLeafDiffer: Differ[MultiLevelSealed.Nested.NestedLeaf] =
      Differ.derived[MultiLevelSealed.Nested.NestedLeaf].ignoreAt(_.value)

    val subject: Differ[MultiLevelSealed] = Differ.derivedDeep[MultiLevelSealed]

    assertConsoleDiffOutput(
      subject,
      MultiLevelSealed.Nested.NestedLeaf("a"),
      MultiLevelSealed.Nested.NestedLeaf("b"),
      s"""NestedLeaf(
         |  value: $grayIgnoredStr
         |)""".stripMargin,
    )
  }

  test("derivedDeep derives mutually referencing sealed traits") {
    val leftSubject: Differ[MutualSealedLeft] = Differ.derivedDeep[MutualSealedLeft]
    val rightSubject: Differ[MutualSealedRight] = Differ.derivedDeep[MutualSealedRight]

    val left =
      MutualSealedLeft.HasRight(
        "left",
        MutualSealedRight.HasLeft("right", MutualSealedLeft.Leaf("leaf")),
      )
    val leftWithChangedLeaf =
      MutualSealedLeft.HasRight(
        "left",
        MutualSealedRight.HasLeft("right", MutualSealedLeft.Leaf("changed")),
      )
    val leftWithChangedRightSubtype =
      MutualSealedLeft.HasRight("left", MutualSealedRight.Leaf("right"))

    assertEquals(leftSubject.diff(left, left).isOk, true)
    assertEquals(leftSubject.diff(left, leftWithChangedLeaf).isOk, false)
    assertEquals(leftSubject.diff(left, leftWithChangedRightSubtype).isOk, false)

    val right = MutualSealedRight.HasLeft("right", MutualSealedLeft.Leaf("leaf"))
    val rightWithChangedLeft = MutualSealedRight.HasLeft("right", MutualSealedLeft.Leaf("changed"))

    assertEquals(rightSubject.diff(right, right).isOk, true)
    assertEquals(rightSubject.diff(right, rightWithChangedLeft).isOk, false)
  }

  test("derived reports missing field differ for mutually referencing sealed traits") {
    val result = stripAnsi(compileErrors("""
        import difflicious.*
        import difflicious.testtypes.*

        val subject: Differ[MutualSealedLeft] = Differ.derived[MutualSealedLeft]
        """))

    assertStartsWith(
      result,
      """error:
        |Failed to derive Differ[difflicious.testtypes.MutualSealedLeft]
        |
        |difflicious.testtypes.MutualSealedLeft
        |  Differ[difflicious.testtypes.MutualSealedLeft.HasRight] cannot be derived because...
        |    Differ[difflicious.testtypes.MutualSealedRight] cannot be found
        |
        |Summary: Derivation failed because we couldn't find Differ[difflicious.testtypes.MutualSealedRight]""".stripMargin,
    )
  }

  test("Derivation failure: All missing case class field instances are reported") {
    val result = stripAnsi(compileErrors("""
        import difflicious.*
        import difflicious.testtypes.*

        val subject: Differ[SimpleCaseClassSubject] = Differ.derived[SimpleCaseClassSubject]
        """))
    assertStartsWith(
      result,
      """error:
         |Failed to derive Differ[difflicious.testtypes.SimpleCaseClassSubject]
         |
         |difflicious.testtypes.SimpleCaseClassSubject
         |  Differ[difflicious.testtypes.SimpleCaseClass1] cannot be found
         |  Differ[difflicious.testtypes.SimpleCaseClass2] cannot be found
         |  Differ[difflicious.testtypes.SimpleCaseClass3] cannot be found
         |
         |Summary: Derivation failed because we couldn't find Differ[difflicious.testtypes.SimpleCaseClass1], Differ[difflicious.testtypes.SimpleCaseClass2], Differ[difflicious.testtypes.SimpleCaseClass3]""".stripMargin,
    )
  }

  test("Derivation failure: Nested case class failures are reported as a tree") {
    val result = stripAnsi(compileErrors("""
        import difflicious.*
        import difflicious.testtypes.*

        val subject: Differ[DerivationFailureSubject] = Differ.derivedDeep[DerivationFailureSubject]
        """))

    assertStartsWith(
      result,
      """error:
        |Failed to derive Differ[difflicious.testtypes.DerivationFailureSubject]
        |
        |difflicious.testtypes.DerivationFailureSubject
        |  Differ[difflicious.testtypes.SomeTrait] cannot be found or derived
        |  Differ[difflicious.testtypes.DerivationFailureNested] cannot be derived because...
        |    Differ[difflicious.testtypes.SomeTrait] cannot be found or derived
        |    Differ[difflicious.testtypes.SomeOtherTrait] cannot be found or derived
        |    Differ[scala.collection.immutable.List[difflicious.testtypes.SomeTrait]] cannot be derived because...
        |      Differ[difflicious.testtypes.SomeTrait] cannot be found or derived
        |
        |Summary: Derivation failed because we couldn't find or derive Differ[difflicious.testtypes.SomeTrait], Differ[difflicious.testtypes.SomeOtherTrait]""".stripMargin,
    )
  }

  test("Derivation failure: Map reports missing key ValueDiffer and value Differ") {
    val result = normalizeMapTypeSpacing(stripAnsi(compileErrors("""
        import difflicious.*
        import difflicious.testtypes.*

        val subject: Differ[MapDerivationFailureSubject] = Differ.derivedDeep[MapDerivationFailureSubject]
        """)))

    assertStartsWith(
      result,
      """error:
        |Failed to derive Differ[difflicious.testtypes.MapDerivationFailureSubject]
        |
        |difflicious.testtypes.MapDerivationFailureSubject
        |  Differ[scala.collection.immutable.Map[difflicious.testtypes.MissingMapKey,difflicious.testtypes.MissingMapValue]] cannot be derived because...
        |    ValueDiffer[difflicious.testtypes.MissingMapKey] cannot be found
        |    Differ[difflicious.testtypes.MissingMapValue] cannot be found or derived
        |
        |Summary: Derivation failed because we couldn't find ValueDiffer[difflicious.testtypes.MissingMapKey] and couldn't find or derive Differ[difflicious.testtypes.MissingMapValue]""".stripMargin,
    )
  }

  test("Derivation failure: Sealed trait failures are reported as a tree") {
    val result = stripAnsi(compileErrors("""
        import difflicious.*
        import difflicious.testtypes.*

        val subject: Differ[DerivationFailureSealed] = Differ.derivedDeep[DerivationFailureSealed]
        """))

    assertStartsWith(
      result,
      """error:
        |Failed to derive Differ[difflicious.testtypes.DerivationFailureSealed]
        |
        |difflicious.testtypes.DerivationFailureSealed
        |  Differ[difflicious.testtypes.DerivationFailureSealed.Direct] cannot be derived because...
        |    Differ[difflicious.testtypes.SomeTrait] cannot be found or derived
        |  Differ[difflicious.testtypes.DerivationFailureSealed.Nested] cannot be derived because...
        |    Differ[difflicious.testtypes.DerivationFailureNested] cannot be derived because...
        |      Differ[difflicious.testtypes.SomeTrait] cannot be found or derived
        |      Differ[difflicious.testtypes.SomeOtherTrait] cannot be found or derived
        |      Differ[scala.collection.immutable.List[difflicious.testtypes.SomeTrait]] cannot be derived because...
        |        Differ[difflicious.testtypes.SomeTrait] cannot be found or derived
        |
        |Summary: Derivation failed because we couldn't find or derive Differ[difflicious.testtypes.SomeTrait], Differ[difflicious.testtypes.SomeOtherTrait]""".stripMargin,
    )
  }

  private def assertRecursiveCustomListDiffer(subject: Differ[RecursiveNodeWithCustomList]): Unit = {
    val left = recursiveNodeWithCustomList(recursiveNodeWithCustomList())
    val right = recursiveNodeWithCustomList(
      recursiveNodeWithCustomList(
        recursiveNodeWithCustomList(),
      ),
    )

    assertEquals(subject.diff(left, left).isOk, true)
    assertEquals(subject.diff(left, right).isOk, false)
  }

  private def normalizeMapTypeSpacing(value: String): String =
    value.replace(
      "scala.collection.immutable.Map[difflicious.testtypes.MissingMapKey, difflicious.testtypes.MissingMapValue]",
      "scala.collection.immutable.Map[difflicious.testtypes.MissingMapKey,difflicious.testtypes.MissingMapValue]",
    )

}

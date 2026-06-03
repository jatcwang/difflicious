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
    val result = compileErrors("""
        import difflicious.*
        import difflicious.testtypes.*

        val subject: Differ[MutualSealedLeft] = Differ.derived[MutualSealedLeft]
        """)

    assert(
      result.contains("Failed to derive Differ[MutualSealedLeft]"),
      s"Expected MutualSealedLeft derivation to fail, got:\n$result",
    )
    assert(
      result.contains("Differ[MutualSealedRight] cannot be found"),
      s"Expected the error to report the missing MutualSealedRight field differ, got:\n$result",
    )
    assert(
      !result.contains("Differ[MutualSealedRight] cannot be found or derived"),
      s"Expected non-deep derivation to report that MutualSealedRight cannot be found, got:\n$result",
    )
    assert(
      !result.contains("right: Differ[MutualSealedRight]"),
      s"Expected the error to omit field names, got:\n$result",
    )
  }

  test("Derivation failure: All missing case class field instances are reported") {
    val result = compileErrors("""
        import difflicious.*
        import difflicious.testtypes.*

        val subject: Differ[SimpleCaseClassSubject] = Differ.derived[SimpleCaseClassSubject]
        """)
    assertStartsWith(
      result,
      """error:
         |Failed to derive Differ[SimpleCaseClassSubject]
         |
         |SimpleCaseClassSubject
         |  Differ[SimpleCaseClass1] cannot be found
         |  Differ[SimpleCaseClass2] cannot be found
         |  Differ[SimpleCaseClass3] cannot be found
         |""".stripMargin,
    )
  }

  test("Derivation failure: Nested case class failures are reported as a tree") {
    val result = compileErrors("""
        import difflicious.*
        import difflicious.testtypes.*

        val subject: Differ[DerivationFailureSubject] = Differ.derivedDeep[DerivationFailureSubject]
        """)

    assert(
      result.contains(
        """Failed to derive Differ[DerivationFailureSubject]
          |
          |DerivationFailureSubject
          |  Differ[SomeTrait] cannot be found or derived
          |  Differ[DerivationFailureNested] cannot be derived because...
          |    Differ[SomeTrait] cannot be found or derived
          |    Differ[SomeOtherTrait] cannot be found or derived
          |
          |Summary: Derivation failed because we couldn't derive Differ[SomeTrait], Differ[SomeOtherTrait]""".stripMargin,
      ),
      s"Expected nested derivation failure tree, got:\n$result",
    )
  }

  test("Derivation failure: Sealed trait failures are reported as a tree") {
    val result = compileErrors("""
        import difflicious.*
        import difflicious.testtypes.*

        val subject: Differ[DerivationFailureSealed] = Differ.derivedDeep[DerivationFailureSealed]
        """)

    assert(
      result.contains(
        """Failed to derive Differ[DerivationFailureSealed]
          |
          |DerivationFailureSealed
          |  Differ[Direct] cannot be derived because...
          |    Differ[SomeTrait] cannot be found or derived
          |  Differ[Nested] cannot be derived because...
          |    Differ[DerivationFailureNested] cannot be derived because...
          |      Differ[SomeTrait] cannot be found or derived
          |      Differ[SomeOtherTrait] cannot be found or derived
          |
          |Summary: Derivation failed because we couldn't derive Differ[SomeTrait], Differ[SomeOtherTrait]""".stripMargin,
      ),
      s"Expected sealed derivation failure tree, got:\n$result",
    )
  }

}

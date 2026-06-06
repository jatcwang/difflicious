package difflicioustest

import difflicious.testutils.*
import difflicious.testtypes.*
import difflicious.Differ

class DifferDerivationTreeSpec extends munit.FunSuite {

  test("derivedDeep fully expanded syntax tree uses available field instances and derives + cache missing ones") {
    implicit val d1: Differ[TreeCaseClass1] = Differ.derived[TreeCaseClass1]
    val tree = ExpandedTree.expandTreeAndSimplify(Differ.derivedDeep[TreeContainer])
    val _ = d1

    assertNoDiff(
      tree,
      Snapshot.read(SnapshotPaths("DifferDerivationTreeSpec", "derivedDeep-tree.scala")),
    )
  }

  test("derived recursive syntax tree uses the lazy recursive instance") {
    val tree = ExpandedTree.expandTreeAndSimplify(Differ.derived[RecursiveDerivedNode])

    assertNoDiff(
      tree,
      Snapshot.read(SnapshotPaths("DifferDerivationTreeSpec", "derived-recursive-tree.scala")),
    )
  }

  test("derivedDeep recursive syntax tree uses the lazy recursive instance") {
    val tree = ExpandedTree.expandTreeAndSimplify(Differ.derivedDeep[RecursiveDerivedDeepNode])

    assertNoDiff(
      tree,
      Snapshot.read(SnapshotPaths("DifferDerivationTreeSpec", "derivedDeep-recursive-tree.scala")),
    )
  }
}

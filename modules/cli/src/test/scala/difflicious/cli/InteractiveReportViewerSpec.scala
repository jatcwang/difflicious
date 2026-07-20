package difflicious.cli

import difflicious.DiffResult
import difflicious.PairType
import difflicious.utils.TypeName
import io.circe.parser.parse
import munit.FunSuite
import snapshot4s.generated.*
import snapshot4s.munit.SnapshotAssertions
import difflicious.cli.InteractiveReportViewer.InteractiveReportViewerState

import java.time.{Instant, ZoneId}
import scala.collection.immutable.ListMap

class InteractiveReportViewerSpec extends FunSuite with SnapshotAssertions {
  private val RunId = "01ARZ3NDEKTSV4RRFFQ69G5FAV"
  private val OtherRunId = "01ARZ3NDEKTSV4RRFFQ69G5FAX"
  private val LaterRunId = "01BRZ3NDEKTSV4RRFFQ69G5FAV"
  private val NewestRunId = "01CRZ3NDEKTSV4RRFFQ69G5FAV"
  private val TestId = "01ARZ3NDEKTSV4RRFFQ69G5FAW"
  private val Expanded = "\u25be"
  private val Collapsed = "\u25b8"
  private val TerminalWidth = 100
  private val TerminalHeight = 48
  private val SnapshotZoneId = ZoneId.of("Europe/Paris")

  snapshotTest("multiple-run report opens the test finder") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first"), diffRun("OtherSuite", "second")))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.assertSnapshot("test-finder")
    testDriver.pressKey(SearchKey.Cancel)
    assert(!testDriver.isTerminated)
    assert(testDriver.render.last.contains("Press ESC again to exit."))
    testDriver.pressKey(SearchKey.Cancel)
    assert(testDriver.isTerminated)
  }

  test("test finder escape cancels an active search without exiting or arming exit") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first"), diffRun("OtherSuite", "second")))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.typeKeys("second")
    testDriver.pressKey(SearchKey.Cancel)

    assert(!testDriver.isTerminated)
    assert(testDriver.render.exists(_.contains("Search: ")))
    assert(!testDriver.render.exists(_.contains("Press ESC again to exit.")))
  }

  snapshotTest("test finder - opens selected report result") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first"), diffRun("OtherSuite", "second")))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.typeKeys("second")
    testDriver.assertSnapshot("search-second")

    testDriver.pressKey(SearchKey.Submit)
    testDriver.assertSnapshot("selected-report-result")
  }

  snapshotTest("hotkey popup renders session keymap labels") {
    val customKeymap = TerminalKeymap.default.copy(
      search = TerminalKeymap.bind(TerminalKey.Search, TerminalKeymap.char('x')),
    )
    val testDriver = TestDriver(makeViewerState(DiffReport(Vector.empty), keymap = customKeymap, color = false))

    testDriver.pressKey(TerminalKey.Help)
    testDriver.assertSnapshot("hotkey-popup")
  }

  test("opened report result shows test context above diff") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first")))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(SearchKey.Submit)
    val diffScreen = testDriver.render
    assertFileSnapshot(
      snapshotLines(diffScreen),
      "InteractiveReportViewerSpec/opened-report-result.snap",
    )
  }

  snapshotTest("test finder - shows run id local timestamp from ULID") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first"), diffRun("OtherSuite", "second")))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.assertSnapshot("test-finder")
  }

  test("timestamp label elides date when timestamp is today") {
    val zone = ZoneId.of("Europe/London")
    val timestamp = Instant.parse("2026-07-15T09:15:10Z")
    val now = Instant.parse("2026-07-15T20:00:00Z")

    assertEquals(
      InteractiveReportViewer.timestampLabel(timestamp.toEpochMilli, now, zone),
      "10:15:10",
    )
  }

  test("timestamp label includes local date when timestamp is not today") {
    val zone = ZoneId.of("Europe/London")
    val timestamp = Instant.parse("2026-07-15T09:15:10Z")
    val now = Instant.parse("2026-07-16T20:00:00Z")

    assertEquals(
      InteractiveReportViewer.timestampLabel(timestamp.toEpochMilli, now, zone),
      "2026-07-15 10:15:10",
    )
  }

  snapshotTest("test finder - select different tests with up and down key") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first"), diffRun("OtherSuite", "second")))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressSearchKeys(SearchKey.Down, SearchKey.Up, SearchKey.Down)
    testDriver.assertSnapshot("selected-second")
  }

  snapshotTest("test finder - hierarchical search highlights suite folders and filters nonmatching folders") {
    val report =
      DiffReport(
        Vector(
          diffRun("AnotherSuite", "deep order invoice snapshot has nested differences", runId = NewestRunId),
          diffRun("ComplexSuite", "list of case class values has a missing whole value", runId = OtherRunId),
        ),
      )
    val testDriver = TestDriver(makeViewerState(report, color = true))

    testDriver.typeKeys("another")
    testDriver.assertSnapshot("filtered-another")
  }

  snapshotTest("test finder - fuzzily highlights displayed test names") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first"), diffRun("OtherSuite", "second")))
    val testDriver = TestDriver(makeViewerState(report, color = true))

    testDriver.typeKeys("scn")
    testDriver.assertSnapshot("highlight-second")
    testDriver.pressKey(SearchKey.Submit)
    testDriver.pressKey(TerminalKey.Escape)
    testDriver.assertSnapshot("selected-result")
  }

  snapshotTest("test finder - flat view shows test name with colored run timestamp") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first")))
    val testDriver = TestDriver(makeViewerState(report, color = true))

    testDriver.pressKey(SearchKey.ToggleHierarchy)
    testDriver.assertSnapshot("flat-view")
  }

  snapshotTest("test finder - renders selected candidate details in a separate single-line box") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first")))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.assertSnapshot("separate-details-box")
  }

  snapshotTest("test finder - snapshots the result picker and selected details") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first")))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.assertSnapshot("picker-and-details")
  }

  snapshotTest("test finder - orders duplicate test names by newest run id") {
    val report =
      DiffReport(
        Vector(
          diffRun("OldSuite", "same", runId = RunId),
          diffRun("LatestSuite", "same", runId = LaterRunId),
        ),
      )
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.assertSnapshot("newest-run-selected")
  }

  test("test finder - fuzzy search returns visible test, suite, and run id matches ordered by newest run timestamp") {
    val report =
      DiffReport(
        Vector(
          diffRun("AnotherSuite", "deep order invoice snapshot has nested differences", runId = NewestRunId),
          diffRun("ComplexSuite", "another test that fails", runId = LaterRunId),
          diffRun("ComplexSuite", "list of case class values has a missing whole value", runId = OtherRunId),
          diffRun("ComplexSuite", "another test that fails", runId = RunId),
        ),
      )

    val matches = InteractiveReportViewer.testSearchMatches(report.runs, "another")

    assertEquals(
      matches.map(searchMatch => searchMatch.candidate.testName),
      Vector(
        "deep order invoice snapshot has nested differences",
        "another test that fails",
        "another test that fails",
      ),
    )
    assertEquals(
      matches.map(searchMatch => searchMatch.candidate.runId),
      Vector(Some(NewestRunId), Some(LaterRunId), Some(RunId)),
    )
  }

  test("test finder - fuzzy search includes suite metadata matches") {
    val report =
      DiffReport(
        Vector(
          diffRun("AnotherSuite", "deep order invoice snapshot has nested differences", runId = NewestRunId),
          diffRun("ComplexSuite", "list of case class values has a missing whole value", runId = OtherRunId),
        ),
      )

    val matches = InteractiveReportViewer.testSearchMatches(report.runs, "another")

    assertEquals(
      matches.map(searchMatch => searchMatch.candidate.testName),
      Vector("deep order invoice snapshot has nested differences"),
    )
  }

  snapshotTest("test finder - searches test ids and labels matching entries") {
    val matchingTestId = "stable-test-identifier"
    val report =
      DiffReport(
        Vector(
          diffRun("ExampleSuite", "first", testId = matchingTestId),
          diffRun("OtherSuite", "second"),
        ),
      )
    val testDriver = TestDriver(makeViewerState(report, color = true))

    testDriver.typeKeys("identifier")

    val matches = InteractiveReportViewer.testSearchMatches(report.runs, "identifier")
    assertEquals(matches.map(_.candidate.testId), Vector(Some(matchingTestId)))
    assert(matches.head.testIdMatch.nonEmpty)
    testDriver.assertSnapshot("hierarchical-test-id-match")

    testDriver.pressKey(SearchKey.ToggleHierarchy)
    testDriver.assertSnapshot("flat-test-id-match")
  }

  snapshotTest("test finder - filters visible test-name matches before ordering by newest run timestamp") {
    val report =
      DiffReport(
        Vector(
          diffRun("AnotherSuite", "deep order invoice snapshot has nested differences", runId = NewestRunId),
          diffRun("ComplexSuite", "another test that fails", runId = LaterRunId),
          diffRun("ComplexSuite", "list of case class values has a missing whole value", runId = OtherRunId),
          diffRun("ComplexSuite", "another test that fails", runId = RunId),
        ),
      )
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.typeKeys("another")
    testDriver.assertSnapshot("filtered-another")
  }

  test("diff screen ctrl-p fuzzy searches report tests") {
    val report = DiffReport(Vector(diffRun("ExampleSuite", "first"), diffRun("OtherSuite", "second")))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(SearchKey.Submit)
    testDriver.pressKey(TerminalKey.Search)
    testDriver.typeKeys("second")
    testDriver.pressKey(SearchKey.Submit)
    val openedScreen = testDriver.render
    assertFileSnapshot(
      snapshotLines(openedScreen),
      "InteractiveReportViewerSpec/diff-screen-ctrl-p-result.snap",
    )
  }

  test("diff screen renders a structured tree and folds ok branches by default") {
    val report = DiffReport(Vector(nestedDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    val rendered = testDriver.render
    assertFileSnapshot(
      snapshotLines(rendered),
      "InteractiveReportViewerSpec/diff-tree-subset.snap",
    )
  }

  snapshotTest("diff screen question mark shows hotkey popup") {
    val report = DiffReport(Vector(nestedDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))
    val baseScreen = testDriver.render

    testDriver.pressKey(TerminalKey.Help)
    testDriver.assertSnapshot("hotkey-popup")

    testDriver.pressKey(TerminalKey.Escape)
    assertEquals(testDriver.render, baseScreen)
  }

  test("diff screen slash searches field names under anchor") {
    val report = DiffReport(Vector(nestedDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.FieldSearch)
    testDriver.typeKeys("same")
    testDriver.pressKey(SearchKey.Submit)
    val rendered = testDriver.render
    assertFileSnapshot(
      snapshotLines(rendered),
      "InteractiveReportViewerSpec/diff-screen-field-search-under-anchor.snap",
    )
  }

  test("diff screen slash searches map keys") {
    val report = DiffReport(Vector(mapKeyDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.FieldSearch)
    testDriver.typeKeys("target")
    testDriver.pressKey(SearchKey.Submit)
    assertFileSnapshot(
      snapshotLines(testDriver.render),
      "InteractiveReportViewerSpec/diff-screen-map-key-search.snap",
    )
  }

  test("diff screen field search fuzzily matches field names") {
    val report = DiffReport(Vector(fieldSearchDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.FieldSearch)
    testDriver.typeKeys("ln")
    testDriver.pressKey(SearchKey.Submit)
    assertFileSnapshot(
      snapshotLines(testDriver.render),
      "InteractiveReportViewerSpec/diff-screen-fuzzy-field-search.snap",
    )
  }

  test("diff screen clears field search input after submit and cancel") {
    val report = DiffReport(Vector(fieldSearchDiffRun))
    val submittedDriver = TestDriver(makeViewerState(report, color = false))

    submittedDriver.pressKey(TerminalKey.FieldSearch)
    submittedDriver.typeKeys("last")
    submittedDriver.pressKey(SearchKey.Submit)
    submittedDriver.pressKey(TerminalKey.FieldSearch)
    val reopenedAfterSubmit = submittedDriver.render
    assertFileSnapshot(
      snapshotLines(reopenedAfterSubmit),
      "InteractiveReportViewerSpec/diff-screen-cleared-search-after-submit.snap",
    )

    val canceledDriver = TestDriver(makeViewerState(report, color = false))

    canceledDriver.pressKey(TerminalKey.FieldSearch)
    canceledDriver.typeKeys("last")
    canceledDriver.pressKey(SearchKey.Cancel)
    canceledDriver.pressKey(TerminalKey.FieldSearch)
    val reopenedAfterCancel = canceledDriver.render
    assertFileSnapshot(
      snapshotLines(reopenedAfterCancel),
      "InteractiveReportViewerSpec/diff-screen-cleared-search-after-cancel.snap",
    )
  }

  test("diff screen shows hidden descendant field search match count badges") {
    val report = DiffReport(Vector(fieldSearchBadgeRun))
    val testDriver = TestDriver(makeViewerState(report, color = true))

    testDriver.pressKeys(TerminalKey.Collapse, TerminalKey.Collapse, TerminalKey.FieldSearch)
    testDriver.typeKeys("name")
    val searchScreen = testDriver.render
    assertFileSnapshot(
      rawSnapshotLines(searchScreen),
      "InteractiveReportViewerSpec/diff-screen-hidden-match-badges.snap",
    )
  }

  test("diff screen hides field search match count badges for visible matches") {
    val report = DiffReport(Vector(fieldSearchBadgeRun))
    val testDriver = TestDriver(makeViewerState(report, color = true))

    testDriver.pressKey(TerminalKey.FieldSearch)
    testDriver.typeKeys("name")
    val searchScreen = testDriver.render
    assertFileSnapshot(
      rawSnapshotLines(searchScreen),
      "InteractiveReportViewerSpec/diff-screen-visible-matches-no-badges.snap",
    )
  }

  test("diff screen hides field search match count badges for expanded ancestors") {
    val report = DiffReport(Vector(nestedFieldSearchBadgeRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKeys(TerminalKey.Collapse, TerminalKey.Collapse, TerminalKey.FieldSearch)
    testDriver.typeKeys("name")
    val searchScreen = testDriver.render
    assertFileSnapshot(
      rawSnapshotLines(searchScreen),
      "InteractiveReportViewerSpec/diff-screen-expanded-ancestor-badges.snap",
    )
  }

  test("diff screen highlights field search matches without moving selection") {
    val report = DiffReport(Vector(nestedDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = true))

    testDriver.pressKey(TerminalKey.FieldSearch)
    testDriver.typeKeys("same")
    val searchScreen = testDriver.render
    assertFileSnapshot(
      rawSnapshotLines(searchScreen),
      "InteractiveReportViewerSpec/diff-screen-highlighted-field-search.snap",
    )
  }

  test("diff screen n and shift-n navigate field search results") {
    val report = DiffReport(Vector(fieldSearchDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.FieldSearch)
    testDriver.typeKeys("name")
    testDriver.pressKey(SearchKey.Submit)
    testDriver.pressKey(TerminalKey.NextSearchResult)
    val afterNext = testDriver.render
    assertFileSnapshot(
      snapshotLines(afterNext),
      "InteractiveReportViewerSpec/diff-screen-next-field-search-result.snap",
    )
    testDriver.pressKey(TerminalKey.PreviousSearchResult)
    assertFileSnapshot(
      snapshotLines(testDriver.render),
      "InteractiveReportViewerSpec/diff-screen-previous-field-search-result.snap",
    )
  }

  test("diff screen ctrl-u clears field search text") {
    val report = DiffReport(Vector(fieldSearchDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.FieldSearch)
    testDriver.typeKeys("first")
    testDriver.pressKey(SearchKey.Clear)
    testDriver.typeKeys("last")
    val searchScreen = testDriver.render
    assertFileSnapshot(
      snapshotLines(searchScreen) + "\n",
      "InteractiveReportViewerSpec/diff-screen-clear-field-search.snap",
    )
  }

  test("diff screen escape exits field search mode") {
    val report = DiffReport(Vector(fieldSearchDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.FieldSearch)
    testDriver.typeKeys("last")
    testDriver.pressKey(SearchKey.Cancel)
    assert(!testDriver.isTerminated)
    testDriver.pressKey(TerminalKey.Next)
    assertFileSnapshot(
      snapshotLines(testDriver.render),
      "InteractiveReportViewerSpec/diff-screen-cancel-field-search.snap",
    )
  }

  test("diff screen colors tree rows by result kind") {
    val report = DiffReport(Vector(coloredTreeDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = true))

    val rendered = testDriver.render
    assertFileSnapshot(
      rawSnapshotLines(rendered),
      "InteractiveReportViewerSpec/diff-screen-result-colors.snap",
    )
  }

  test("diff screen arrow navigation can focus and expand ok branches") {
    val report = DiffReport(Vector(nestedDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKeys(TerminalKey.Previous, TerminalKey.Previous)
    val focusedOk = testDriver.render
    assertSelectedLineContains(focusedOk, "same ->")
    assertNoDiffFooter(focusedOk)

    testDriver.pressKey(TerminalKey.Expand)
    val expandedOk = testDriver.render
    assertFileSnapshot(
      snapshotLines(expandedOk),
      "InteractiveReportViewerSpec/diff-screen-expanded-ok-branch.snap",
    )
  }

  test("diff screen indents leaf fields below expandable parents") {
    val report = DiffReport(Vector(nestedDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKeys(TerminalKey.Previous, TerminalKey.Previous, TerminalKey.Expand)
    val rendered = testDriver.render
    val parentLine = lineContaining(rendered, s"$Expanded same ->")
    val childLine = lineContaining(rendered, "nested -> 1")
    assertEquals(childLine.indexOf("nested ->"), parentLine.indexOf("same ->") + 2)
  }

  test("diff screen bracket keys expand and collapse the selected subtree") {
    val report = DiffReport(Vector(bracketSubtreeDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKeys(TerminalKey.Collapse, TerminalKey.CollapseLevel)
    val collapsedSelected = testDriver.render
    assertFileSnapshot(
      snapshotLines(collapsedSelected),
      "InteractiveReportViewerSpec/diff-screen-bracket-collapsed-subtree.snap",
    )

    testDriver.pressKey(TerminalKey.ExpandLevel)
    val expandedSelected = testDriver.render
    assertFileSnapshot(
      snapshotLines(expandedSelected),
      "InteractiveReportViewerSpec/diff-screen-bracket-expanded-subtree.snap",
    )
  }

  test("diff screen bracket keys do nothing on selected leaves") {
    val report = DiffReport(Vector(singleNestedDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKeys(TerminalKey.ExpandLevel, TerminalKey.CollapseLevel)
    val rendered = testDriver.render
    assertFileSnapshot(
      snapshotLines(rendered),
      "InteractiveReportViewerSpec/diff-screen-bracket-keys-on-leaf.snap",
    )
  }

  test("diff screen arrow navigation does not wrap at boundaries") {
    val report = DiffReport(Vector(nestedDiffRun))
    val bottomDriver = TestDriver(makeViewerState(report, color = false))

    bottomDriver.pressKey(TerminalKey.Next)
    assertFileSnapshot(
      snapshotLines(bottomDriver.render),
      "InteractiveReportViewerSpec/diff-screen-bottom-boundary.snap",
    )

    val topDriver = TestDriver(makeViewerState(report, color = false))

    topDriver.pressKeys(TerminalKey.Previous, TerminalKey.Previous, TerminalKey.Previous, TerminalKey.Previous)
    assertFileSnapshot(
      snapshotLines(topDriver.render),
      "InteractiveReportViewerSpec/diff-screen-top-boundary.snap",
    )
  }

  test("diff screen anchors selected subtree and can return to top") {
    val report = DiffReport(Vector(rawDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.Anchor)
    assertFileSnapshot(
      snapshotLines(testDriver.render),
      "InteractiveReportViewerSpec/diff-screen-anchored-subtree.snap",
    )
    testDriver.pressKey(TerminalKey.Top)
    assertNoSubtreeHeader(testDriver.render)
  }

  test("diff screen escape deanchors one step before quitting") {
    val report = DiffReport(Vector(rawDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.Anchor)
    assertFileSnapshot(
      snapshotLines(testDriver.render),
      "InteractiveReportViewerSpec/diff-screen-escape-anchored-subtree.snap",
    )
    testDriver.pressKey(TerminalKey.Escape)
    assertNoSubtreeHeader(testDriver.render)
    testDriver.pressKey(TerminalKey.Escape)
    assertNoSubtreeHeader(testDriver.render)
    assert(!testDriver.isTerminated)
    assert(testDriver.render.last.contains("Press ESC again to exit."))
    testDriver.pressKey(TerminalKey.Escape)
    assert(testDriver.isTerminated)
  }

  test("diff screen requires consecutive escapes to exit") {
    val report = DiffReport(Vector(rawDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.Escape)
    assert(!testDriver.isTerminated)
    assert(testDriver.render.last.contains("Press ESC again to exit."))

    testDriver.pressKey(TerminalKey.Next)
    assert(!testDriver.render.exists(_.contains("Press ESC again to exit.")))

    testDriver.pressInputs(Vector(27, 27))
    assert(testDriver.isTerminated)
  }

  test("ctrl-c quits immediately") {
    val report = DiffReport(Vector(rawDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.Quit)

    assert(testDriver.isTerminated)
  }

  test("diff screen next and previous diff reveal folded descendants") {
    val report = DiffReport(Vector(nestedDiffRun))

    val nextDriver = TestDriver(makeViewerState(report, color = false))

    nextDriver.pressKeys(TerminalKey.Collapse, TerminalKey.Collapse)
    val nextFolded = nextDriver.render
    assertFileSnapshot(
      snapshotLines(nextFolded),
      "InteractiveReportViewerSpec/diff-screen-next-diff-folded.snap",
    )
    nextDriver.pressKey(TerminalKey.NextDifference)
    assertFileSnapshot(
      snapshotLines(nextDriver.render),
      "InteractiveReportViewerSpec/diff-screen-next-diff-revealed.snap",
    )

    val previousDriver = TestDriver(makeViewerState(report, color = false))

    previousDriver.pressKeys(TerminalKey.Collapse, TerminalKey.Collapse)
    val previousFolded = previousDriver.render
    assertEquals(previousFolded, nextFolded)
    previousDriver.pressKey(TerminalKey.PreviousDifference)
    assertFileSnapshot(
      snapshotLines(previousDriver.render),
      "InteractiveReportViewerSpec/diff-screen-previous-diff-revealed.snap",
    )
  }

  test("diff screen next diff does not enter collapsed side-only records") {
    val report = DiffReport(Vector(sideOnlyRecordRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.NextDifference)
    val rendered = testDriver.render
    assertFileSnapshot(
      snapshotLines(rendered),
      "InteractiveReportViewerSpec/diff-screen-collapsed-side-only-records.snap",
    )
  }

  test("diff screen next diff enters side-only records after explicit expansion") {
    val report = DiffReport(Vector(sideOnlyRecordRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKeys(TerminalKey.Expand, TerminalKey.NextDifference)
    val rendered = testDriver.render
    assertFileSnapshot(
      snapshotLines(rendered),
      "InteractiveReportViewerSpec/diff-screen-expanded-side-only-record.snap",
    )
  }

  test("diff screen next diff does not enter default-expanded side-only root") {
    val report = DiffReport(Vector(rootSideOnlyRecordRun))
    val defaultDriver = TestDriver(makeViewerState(report, color = false))

    defaultDriver.pressKey(TerminalKey.NextDifference)
    assertFileSnapshot(
      snapshotLines(defaultDriver.render),
      "InteractiveReportViewerSpec/diff-screen-default-expanded-side-only-root.snap",
    )

    val expandedDriver = TestDriver(makeViewerState(report, color = false))

    expandedDriver.pressKeys(TerminalKey.Expand, TerminalKey.NextDifference)
    assertFileSnapshot(
      snapshotLines(expandedDriver.render),
      "InteractiveReportViewerSpec/diff-screen-explicitly-expanded-side-only-root.snap",
    )
  }

  test("diff screen can expand obtained-only and expected-only records") {
    val report = DiffReport(Vector(sideOnlyRecordRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKeys(TerminalKey.Expand, TerminalKey.Next, TerminalKey.Next, TerminalKey.Expand)
    val rendered = testDriver.render
    assertFileSnapshot(
      snapshotLines(rendered),
      "InteractiveReportViewerSpec/diff-screen-expanded-both-side-only-records.snap",
    )
  }

  test("diff screen right pads record fields to the longest field name") {
    val report = DiffReport(Vector(paddedRecordDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    val rendered = testDriver.render
    assertFileSnapshot(
      snapshotLines(rendered),
      "InteractiveReportViewerSpec/diff-screen-padded-record-fields.snap",
    )
  }

  test("diff screen includes type parameters and toggles short and full type names") {
    val report = DiffReport(Vector(parameterizedTypeDiffRun))
    val testDriver = TestDriver(makeViewerState(report, color = false))

    testDriver.pressKey(TerminalKey.Previous)
    val shortNames = testDriver.render
    assertFileSnapshot(
      snapshotLines(shortNames),
      "InteractiveReportViewerSpec/diff-screen-short-type-names.snap",
    )

    testDriver.pressKey(TerminalKey.ToggleTypeName)
    val fullNames = testDriver.render
    assertFileSnapshot(
      snapshotLines(fullNames),
      "InteractiveReportViewerSpec/diff-screen-full-type-names.snap",
    )
  }

  private def diffRun(
    suiteName: String,
    testName: String,
    runId: String = RunId,
    testId: String = TestId,
  ): DiffRun =
    rawDiffRun
      .copy(
        metadata = Some(
          DiffRunMetadata(
            runId = runId,
            testId = testId,
            suiteName = suiteName,
            suiteId = s"example.$suiteName",
            suiteClassName = Some(s"example.$suiteName"),
            testName = testName,
            testText = testName,
            testHierarchy = Vector(testName),
            fileName = s"$suiteName.scala",
            filePath = s"/workspace/$suiteName.scala",
            lineNumber = 37,
          ),
        ),
      )

  private def rawDiffRun: DiffRun =
    JsonDiffRunner.diff(parse("""{"value": 1}""").toOption.get, parse("""{"value": 2}""").toOption.get)

  private def largeDiffRun: DiffRun = {
    val result =
      DiffResult.RecordResult(
        typeName = TypeName[Any]("difflicious.cli.LargeFixture", "LargeFixture", Nil),
        fields = ListMap.from(
          (1 to 30).map { index =>
            s"field$index" -> DiffResult.ValueResult
              .Both(index.toString, (index + 1).toString, isSame = false, isIgnored = false)
          },
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      )

    DiffRun.fromResult(result, metadata = diffRun("ExampleSuite", "first").metadata)
  }

  private def nestedDiffRun: DiffRun =
    JsonDiffRunner.diff(
      parse("""{"same": {"nested": 1}, "diff": {"nested": 1}}""").toOption.get,
      parse("""{"same": {"nested": 1}, "diff": {"nested": 2}}""").toOption.get,
    )

  private def bracketSubtreeDiffRun: DiffRun = {
    def branch: DiffResult.RecordResult =
      DiffResult.RecordResult(
        typeName = TypeName[Any]("difflicious.cli.Branch", "Branch", Nil),
        fields = ListMap(
          "nested" -> DiffResult.ValueResult.Both("1", "2", isSame = false, isIgnored = false),
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      )

    DiffRun.fromResult(
      DiffResult.RecordResult(
        typeName = TypeName[Any]("difflicious.cli.Root", "Root", Nil),
        fields = ListMap(
          "selected" -> branch,
          "other" -> branch,
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      ),
      metadata = None,
    )
  }

  private def singleNestedDiffRun: DiffRun =
    JsonDiffRunner.diff(
      parse("""{"diff": {"nested": 1}}""").toOption.get,
      parse("""{"diff": {"nested": 2}}""").toOption.get,
    )

  private def fieldSearchDiffRun: DiffRun =
    DiffRun.fromResult(
      DiffResult.RecordResult(
        typeName = TypeName[Any]("difflicious.cli.FieldSearchFixture", "FieldSearchFixture", Nil),
        fields = ListMap(
          "firstName" -> DiffResult.ValueResult.Both("Ada", "Grace", isSame = false, isIgnored = false),
          "age" -> DiffResult.ValueResult.Both("1", "2", isSame = false, isIgnored = false),
          "lastName" -> DiffResult.ValueResult.Both("Lovelace", "Hopper", isSame = false, isIgnored = false),
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      ),
      metadata = None,
    )

  private def fieldSearchBadgeRun: DiffRun =
    DiffRun.fromResult(
      DiffResult.RecordResult(
        typeName = TypeName[Any]("difflicious.cli.OrderFixture", "OrderFixture", Nil),
        fields = ListMap(
          "fulfillment" -> DiffResult.RecordResult(
            typeName = TypeName[Any]("difflicious.cli.Fulfillment", "Fulfillment", Nil),
            fields = ListMap(
              "firstName" -> DiffResult.ValueResult.Both("Ada", "Grace", isSame = false, isIgnored = false),
              "lastName" -> DiffResult.ValueResult.Both("Lovelace", "Hopper", isSame = false, isIgnored = false),
            ),
            pairType = PairType.Both,
            isIgnored = false,
            isOk = false,
          ),
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      ),
      metadata = None,
    )

  private def nestedFieldSearchBadgeRun: DiffRun =
    DiffRun.fromResult(
      DiffResult.RecordResult(
        typeName = TypeName[Any]("difflicious.cli.OrderFixture", "OrderFixture", Nil),
        fields = ListMap(
          "fulfillment" -> DiffResult.RecordResult(
            typeName = TypeName[Any]("difflicious.cli.Fulfillment", "Fulfillment", Nil),
            fields = ListMap(
              "contact" -> DiffResult.RecordResult(
                typeName = TypeName[Any]("difflicious.cli.Contact", "Contact", Nil),
                fields = ListMap(
                  "firstName" -> DiffResult.ValueResult.Both("Ada", "Grace", isSame = false, isIgnored = false),
                  "lastName" -> DiffResult.ValueResult.Both("Lovelace", "Hopper", isSame = false, isIgnored = false),
                ),
                pairType = PairType.Both,
                isIgnored = false,
                isOk = false,
              ),
            ),
            pairType = PairType.Both,
            isIgnored = false,
            isOk = false,
          ),
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      ),
      metadata = None,
    )

  private def mapKeyDiffRun: DiffRun =
    DiffRun.fromResult(
      DiffResult.MapResult(
        typeName = TypeName[Any](
          "scala.collection.immutable.Map",
          "Map",
          List(TypeName[Any]("java.lang.String", "String", Nil), TypeName[Any]("java.lang.String", "String", Nil)),
        ),
        entries = Vector(
          DiffResult.MapResult.Entry(
            "\"targetKey\"",
            DiffResult.ValueResult.Both("left", "right", isSame = false, isIgnored = false),
          ),
          DiffResult.MapResult.Entry(
            "\"otherKey\"",
            DiffResult.ValueResult.Both("same", "same", isSame = true, isIgnored = false),
          ),
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      ),
      metadata = None,
    )

  private def coloredTreeDiffRun: DiffRun =
    DiffRun.fromResult(
      DiffResult.RecordResult(
        typeName = TypeName[Any]("difflicious.cli.ColorFixture", "ColorFixture", Nil),
        fields = ListMap(
          "changed" -> DiffResult.ValueResult.Both("1", "2", isSame = false, isIgnored = false),
          "obtained" -> DiffResult.ValueResult.ObtainedOnly("left", isIgnored = false),
          "expected" -> DiffResult.ValueResult.ExpectedOnly("right", isIgnored = false),
          "ignored" -> DiffResult.ValueResult.Both("old", "new", isSame = false, isIgnored = true),
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      ),
      metadata = None,
    )

  private def sideOnlyRecordRun: DiffRun =
    DiffRun.fromResult(
      DiffResult.RecordResult(
        typeName = TypeName[Any]("difflicious.cli.SideOnlyFixture", "SideOnlyFixture", Nil),
        fields = ListMap(
          "actualOnly" -> DiffResult.RecordResult(
            typeName = TypeName[Any]("difflicious.cli.ActualOnlyRecord", "ActualOnlyRecord", Nil),
            fields = ListMap(
              "name" -> DiffResult.ValueResult.ObtainedOnly("Ada", isIgnored = false),
            ),
            pairType = PairType.ObtainedOnly,
            isIgnored = false,
            isOk = false,
          ),
          "expectedOnly" -> DiffResult.RecordResult(
            typeName = TypeName[Any]("difflicious.cli.ExpectedOnlyRecord", "ExpectedOnlyRecord", Nil),
            fields = ListMap(
              "name" -> DiffResult.ValueResult.ExpectedOnly("Grace", isIgnored = false),
            ),
            pairType = PairType.ExpectedOnly,
            isIgnored = false,
            isOk = false,
          ),
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      ),
      metadata = None,
    )

  private def rootSideOnlyRecordRun: DiffRun =
    DiffRun.fromResult(
      DiffResult.RecordResult(
        typeName = TypeName[Any]("difflicious.cli.RootOnlyRecord", "RootOnlyRecord", Nil),
        fields = ListMap(
          "name" -> DiffResult.ValueResult.ObtainedOnly("Ada", isIgnored = false),
        ),
        pairType = PairType.ObtainedOnly,
        isIgnored = false,
        isOk = false,
      ),
      metadata = None,
    )

  private def paddedRecordDiffRun: DiffRun =
    DiffRun.fromResult(
      DiffResult.RecordResult(
        typeName = TypeName[Any]("difflicious.cli.PaddedRecord", "PaddedRecord", Nil),
        fields = ListMap(
          "a" -> DiffResult.ValueResult.Both("1", "2", isSame = false, isIgnored = false),
          "longField" -> DiffResult.ValueResult.Both("3", "4", isSame = false, isIgnored = false),
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      ),
      metadata = None,
    )

  private def parameterizedTypeDiffRun: DiffRun =
    DiffRun.fromResult(
      DiffResult.ListResult(
        typeName = TypeName[Any](
          "scala.collection.immutable.List",
          "List",
          List(TypeName[Any]("difflicious.cli.Item", "Item", Nil)),
        ),
        items = Vector(
          DiffResult.ValueResult.Both("1", "2", isSame = false, isIgnored = false),
        ),
        pairType = PairType.Both,
        isIgnored = false,
        isOk = false,
      ),
      metadata = None,
    )

  private def lineContaining(lines: Vector[String], text: String): String =
    lines.find(_.contains(text)).getOrElse(fail(s"Could not find line containing $text"))

  private def selectedLine(lines: Vector[String]): Option[String] =
    lines.find(line => line.contains("> ") && line.contains("<"))

  private def assertSelectedLineContains(lines: Vector[String], text: String): Unit = {
    val line =
      selectedLine(lines)
        .filter(line => Ansi.strip(line).contains(text))
        .getOrElse(fail(s"Could not find selected line containing $text"))
    assert(line.contains("> "))
    assert(line.contains("<"))
  }

  private def assertNoDiffFooter(lines: Vector[String]): Unit = {
    assert(!lines.exists(_.trim.startsWith("Selected:")))
    assert(!lines.exists(_.trim.startsWith("Kind:")))
    assert(!lines.exists(_.trim.startsWith("Type:")))
  }

  private def assertNoSubtreeHeader(lines: Vector[String]): Unit =
    assert(!lines.exists(_.contains("Viewing diff subtree from:")))

  private def snapshotLines(lines: Vector[String]): String =
    lines.map(Ansi.strip).mkString("\n")

  private def rawSnapshotLines(lines: Vector[String]): String =
    lines.mkString("\n")

  private def makeViewerState(
    report: DiffReport,
    color: Boolean,
    keymap: TerminalKeymap = TerminalKeymap.default,
    initialIndex: Int = 0,
  ): InteractiveReportViewerState =
    InteractiveReportViewerState.initial(
      report = report,
      color = color,
      width = TerminalWidth,
      height = TerminalHeight,
      keymap = keymap,
      initialIndex = initialIndex,
      zoneId = SnapshotZoneId,
    )

  private final case class BaseSnapshotPath(value: String)
  private final case class Subscreen(startLine: Int, numLines: Int, columnSize: Int)

  private def snapshotTest(name: String)(body: BaseSnapshotPath ?=> Any): Unit = {
    assert(
      name.matches("[A-Za-z0-9 _-]+"),
      s"snapshot test name must contain only alphanumeric characters, spaces, dashes, and underscores: $name",
    )
    test(name) {
      given BaseSnapshotPath = BaseSnapshotPath(s"InteractiveReportViewerSpec/$name")
      body
    }
  }

  private final class TestDriver(private var state: InteractiveReportViewerState) {
    private var snapshotCount = 0

    def typeKeys(keys: String): Unit =
      keys.foreach(char => pressKey(SearchKey.Character(char)))

    def pressKey(key: SearchKey): Unit =
      state = state.handleInput(searchKeyInput(key))

    def pressKey(key: TerminalKey): Unit =
      state = state.resize(TerminalWidth, TerminalHeight).handleInput(terminalKeyInput(key, state.keymap))

    def pressKeys(keys: TerminalKey*): Unit =
      keys.foreach(pressKey)

    def pressSearchKeys(keys: SearchKey*): Unit =
      keys.foreach(pressKey)

    def pressInputs(inputs: Vector[Int]): Unit =
      state = state.handleInput(inputs)

    def render: Vector[String] =
      state.render

    def isTerminated: Boolean =
      state.isTerminated

    def assertSnapshot(name: String)(using baseSnapshotPath: BaseSnapshotPath): Unit =
      assertSnapshot(name, None)

    def assertSnapshot(name: String, subscreen: Subscreen)(using baseSnapshotPath: BaseSnapshotPath): Unit =
      assertSnapshot(name, Some(subscreen))

    private def assertSnapshot(name: String, subscreen: Option[Subscreen])(using
      baseSnapshotPath: BaseSnapshotPath,
    ): Unit = {
      snapshotCount += 1
      val path = s"${baseSnapshotPath.value}/${f"$snapshotCount%02d"}-$name.snap"
      val lines =
        subscreen match {
          case Some(Subscreen(startLine, numLines, columnSize)) =>
            state.render.slice(startLine, startLine + numLines).map(_.take(columnSize))
          case None =>
            state.render
        }
      assertFileSnapshot(lines.mkString("\n"), path)
    }
  }

  private def terminalKeyInput(key: TerminalKey, keymap: TerminalKeymap = TerminalKeymap.default): Vector[Int] =
    keymap.terminalKeyInputs.getOrElse(key, keymap.terminalKeyInputs(TerminalKey.Quit))

  private def searchKeyInput(searchKey: SearchKey): Vector[Int] =
    searchKey match {
      case SearchKey.Cancel => terminalKeyInput(TerminalKey.Escape)
      case SearchKey.Quit => terminalKeyInput(TerminalKey.Quit)
      case SearchKey.Submit => Vector(13)
      case SearchKey.Up => Vector(11)
      case SearchKey.Down => Vector(10)
      case SearchKey.Backspace => Vector(127)
      case SearchKey.Clear => Vector(21)
      case SearchKey.ToggleHierarchy => Vector(8)
      case SearchKey.Character(value) => Vector(value.toInt)
      case SearchKey.Ignored => Vector(0)
    }

}

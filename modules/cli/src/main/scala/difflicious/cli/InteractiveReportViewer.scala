package difflicious.cli

import difflicious.DiffResult.MapResult.Entry
import difflicious.{DiffResult, DiffResultPrinter, PairType}
import difflicious.reporter.Ulid
import difflicious.utils.TypeName
import io.circe.parser.decode
import org.jline.terminal.{Attributes, Terminal, TerminalBuilder}
import org.jline.utils.NonBlockingReader

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneId}
import scala.util.control.NonFatal

private[cli] sealed trait TerminalKey

private[cli] object TerminalKey {
  case object Escape extends TerminalKey
  case object Quit extends TerminalKey
  case object Open extends TerminalKey
  case object Next extends TerminalKey
  case object Previous extends TerminalKey
  case object NextDifference extends TerminalKey
  case object PreviousDifference extends TerminalKey
  case object FieldSearch extends TerminalKey
  case object NextSearchResult extends TerminalKey
  case object PreviousSearchResult extends TerminalKey
  case object PageDown extends TerminalKey
  case object PageUp extends TerminalKey
  case object Expand extends TerminalKey
  case object Collapse extends TerminalKey
  case object ExpandLevel extends TerminalKey
  case object CollapseLevel extends TerminalKey
  case object Anchor extends TerminalKey
  case object Top extends TerminalKey
  case object ToggleTypeName extends TerminalKey
  case object Search extends TerminalKey
  case object Help extends TerminalKey
  case object Ignored extends TerminalKey
}

private[cli] sealed trait SearchKey

private[cli] object SearchKey {
  case object Cancel extends SearchKey
  case object Quit extends SearchKey
  case object Submit extends SearchKey
  case object Up extends SearchKey
  case object Down extends SearchKey
  case object Backspace extends SearchKey
  case object Clear extends SearchKey
  case object ToggleHierarchy extends SearchKey
  final case class Character(value: Char) extends SearchKey
  case object Ignored extends SearchKey
}

private[cli] final case class TerminalKeymap(
  escape: TerminalKeymap.Binding,
  quit: TerminalKeymap.Binding,
  open: TerminalKeymap.Binding,
  next: TerminalKeymap.Binding,
  previous: TerminalKeymap.Binding,
  nextDifference: TerminalKeymap.Binding,
  previousDifference: TerminalKeymap.Binding,
  fieldSearch: TerminalKeymap.Binding,
  nextSearchResult: TerminalKeymap.Binding,
  previousSearchResult: TerminalKeymap.Binding,
  pageDown: TerminalKeymap.Binding,
  pageUp: TerminalKeymap.Binding,
  expand: TerminalKeymap.Binding,
  collapse: TerminalKeymap.Binding,
  expandLevel: TerminalKeymap.Binding,
  collapseLevel: TerminalKeymap.Binding,
  anchor: TerminalKeymap.Binding,
  top: TerminalKeymap.Binding,
  toggleTypeName: TerminalKeymap.Binding,
  search: TerminalKeymap.Binding,
  help: TerminalKeymap.Binding,
) {
  import TerminalKeymap.Input

  private val bindings: Vector[TerminalKeymap.Binding] =
    Vector(
      escape,
      quit,
      open,
      next,
      previous,
      nextDifference,
      previousDifference,
      fieldSearch,
      nextSearchResult,
      previousSearchResult,
      pageDown,
      pageUp,
      expand,
      collapse,
      expandLevel,
      collapseLevel,
      anchor,
      top,
      toggleTypeName,
      search,
      help,
    )

  private val codeBindings: Map[Int, TerminalKey] =
    bindings.flatMap(binding => binding.inputs.collect { case Input.Code(code, _) => code -> binding.key }).toMap

  private val characterBindings: Map[Char, TerminalKey] =
    bindings
      .flatMap(binding => binding.inputs.collect { case Input.Character(char, _) => char -> binding.key })
      .toMap

  private val escapeBindings: Map[Vector[Int], TerminalKey] =
    bindings
      .flatMap(binding => binding.inputs.collect { case Input.Escape(sequence, _) => sequence -> binding.key })
      .toMap

  private val escapePrefixes: Set[Vector[Int]] =
    escapeBindings.keysIterator.flatMap(sequence => (1 to sequence.length).map(sequence.take)).toSet

  def lookupCode(code: Int): Option[TerminalKey] =
    codeBindings.get(code)

  def lookupCharacter(char: Char): Option[TerminalKey] =
    characterBindings.get(char).orElse(characterBindings.get(char.toLower))

  def lookupEscape(sequence: Vector[Int]): Option[TerminalKey] =
    escapeBindings.get(sequence)

  lazy val terminalKeyInputs: Map[TerminalKey, Vector[Int]] =
    bindings.foldLeft(Map.empty[TerminalKey, Vector[Int]]) { case (acc, binding) =>
      if (acc.contains(binding.key)) acc
      else {
        val inputs = binding.inputs.headOption.map(_.codes).getOrElse(Vector.empty)
        acc.updated(binding.key, inputs)
      }
    }

  def isEscapePrefix(sequence: Vector[Int]): Boolean =
    escapePrefixes.contains(sequence)

  def maxEscapeSequenceLength: Int =
    if (escapeBindings.isEmpty) 0 else escapeBindings.keys.map(_.length).max
}

private[cli] object TerminalKeymap {
  final case class Binding(key: TerminalKey, inputs: Vector[Input]) {
    def label: String =
      inputs.map(_.label).distinct.mkString(" or ")
  }

  sealed trait Input {
    def label: String
    def codes: Vector[Int]
  }

  object Input {
    final case class Character(char: Char, label: String) extends Input {
      override def codes: Vector[Int] =
        Vector(char.toInt)
    }

    final case class Code(code: Int, label: String) extends Input {
      override def codes: Vector[Int] =
        Vector(code)
    }

    final case class Escape(sequence: Vector[Int], label: String) extends Input {
      override def codes: Vector[Int] =
        27 +: sequence
    }
  }

  val default: TerminalKeymap = {
    import TerminalKey._

    TerminalKeymap(
      escape = bind(Escape, code(27, "escape")),
      quit = bind(Quit, code(3, "ctrl-c"), code(4, "ctrl-d")),
      open = bind(Open, code(10, "enter"), code(13, "enter"), char('o')),
      next = bind(Next, escape("[B", "down"), char('j')),
      previous = bind(Previous, escape("[A", "up"), char('k')),
      nextDifference = bind(NextDifference, char('f')),
      previousDifference = bind(PreviousDifference, char('b')),
      fieldSearch = bind(FieldSearch, char('/')),
      nextSearchResult = bind(NextSearchResult, char('n')),
      previousSearchResult = bind(PreviousSearchResult, char('N')),
      pageDown = bind(PageDown, escape("[6~", "page down")),
      pageUp = bind(PageUp, escape("[5~", "page up")),
      expand = bind(Expand, escape("[C", "right"), char('l')),
      collapse = bind(Collapse, escape("[D", "left"), char('h')),
      expandLevel = bind(ExpandLevel, char(']')),
      collapseLevel = bind(CollapseLevel, char('[')),
      anchor = bind(Anchor, char('a')),
      top = bind(Top, char('t')),
      toggleTypeName = bind(ToggleTypeName, char('i')),
      search = bind(Search, code(16, "ctrl-p")),
      help = bind(Help, char('?'), escape("OP", "F1"), escape("[11~", "F1"), escape("[[A", "F1")),
    )
  }

  private[cli] def bind(key: TerminalKey, inputs: Input*): Binding =
    Binding(key, inputs.toVector)

  private[cli] def char(value: Char): Input.Character =
    Input.Character(value, value.toString)

  private[cli] def code(value: Int, label: String): Input.Code =
    Input.Code(value, label)

  private[cli] def escape(sequence: String, label: String): Input.Escape =
    Input.Escape(sequence.map(_.toInt).toVector, label)
}

private[cli] trait TerminalSession {
  def keymap: TerminalKeymap =
    TerminalKeymap.default

  def width: Int
  def height: Int
  def readInputBurst(): Vector[Int]
  def render(lines: Vector[String]): Unit
}

private[cli] trait TuiRunner {
  def run(report: DiffReport, color: Boolean, initialIndex: Int): Unit
}

object InteractiveReportViewer extends TuiRunner {
  private val SearchCandidateRows = 6

  override def run(report: DiffReport, color: Boolean, initialIndex: Int = 0): Unit = {
    var terminal: Terminal = null
    var originalAttributes: Attributes = null
    var session: JLineTerminalSession = null

    try {
      terminal = TerminalBuilder.builder().name("difflicious").system(true).build()
      originalAttributes = terminal.enterRawMode()
      session = new JLineTerminalSession(terminal, TerminalKeymap.default)
      session.enterFullscreen()
      runSession(report, session, color, initialIndex)
    } finally {
      if (session != null) ignoreNonFatal(session.exitFullscreen())
      if (terminal != null && originalAttributes != null) ignoreNonFatal(terminal.setAttributes(originalAttributes))
      if (terminal != null) ignoreNonFatal(terminal.close())
    }
  }

  private def ignoreNonFatal(action: => Unit): Unit =
    try action
    catch {
      case NonFatal(_) => ()
    }

  private final case class ParsedTerminalKey(key: TerminalKey, nextIndex: Int)
  private final case class ParsedSearchKey(key: SearchKey, nextIndex: Int)

  private[cli] final case class InteractiveReportViewerState(
    color: Boolean,
    width: Int,
    height: Int,
    keymap: TerminalKeymap,
    zoneId: ZoneId,
    screen: TerminalScreen,
    exitArmed: Boolean,
  ) {
    def handleInput(inputs: Vector[Int]): InteractiveReportViewerState = {
      var model = this
      var index = 0

      while (index < inputs.length && !model.isTerminated) {
        model.screen match {
          case screen if screen.showHelpPopup =>
            model = model.copy(screen = screen.withHelpPopup(show = false), exitArmed = false)
            index = inputs.length

          case _ if model.expectsSearchInput =>
            val parsed = model.parseSearchKey(inputs, index)
            model = model.applySearchKey(parsed.key)
            index = parsed.nextIndex

          case _ =>
            val parsed = model.parseTerminalKey(inputs, index)
            model = model.applyTerminalKey(parsed.key)
            index = parsed.nextIndex
        }
      }

      model
    }

    def render: Vector[String] = {
      val base = screen match {
        case _: TerminalScreen.EmptyReport =>
          Vector("No diff failures found.")
        case s: TerminalScreen.ReportFinder =>
          val matches = searchMatches(s.state.candidates, s.state.query)
          renderSearchPrompt(
            SearchPromptState(s.state.query, s.state.selectedIndex),
            matches,
            s.state.viewMode,
            width,
            height,
            color,
          )
        case s: TerminalScreen.Diff =>
          val normalized = s.state.normalized
          renderDiffScreen(
            normalized.tree,
            normalized.anchor,
            normalized.rows,
            normalized.fieldSearch,
            color,
            normalized.selectedIndex,
            width,
            height,
            normalized.reportPanel,
          )
        case s: TerminalScreen.NoDifferences =>
          renderNoDifferencesScreen(s.state.run, color, s.state.reportPanel, width)
        case TerminalScreen.Terminated =>
          Vector.empty
      }
      val withPopup = if (screen.showHelpPopup) renderHelpPopup(base, width, height, keymap) else base
      if (exitArmed) renderBottomMessage(withPopup, "Press ESC again to exit.", width, height) else withPopup
    }

    def isTerminated: Boolean =
      screen == TerminalScreen.Terminated

    def resize(width: Int, height: Int): InteractiveReportViewerState =
      copy(width = width, height = height)

    private def expectsSearchInput: Boolean =
      screen match {
        case _: TerminalScreen.ReportFinder => true
        case s: TerminalScreen.Diff => s.state.fieldSearch.active
        case _ => false
      }

    private def applyTerminalKey(key: TerminalKey): InteractiveReportViewerState =
      key match {
        case TerminalKey.Escape => applyEscape()
        case TerminalKey.Quit => copy(screen = TerminalScreen.Terminated, exitArmed = false)
        case _ => copy(exitArmed = false).applyNonEscapeTerminalKey(key)
      }

    private def applyNonEscapeTerminalKey(key: TerminalKey): InteractiveReportViewerState =
      screen match {
        case _: TerminalScreen.EmptyReport =>
          key match {
            case TerminalKey.Open => copy(screen = TerminalScreen.Terminated)
            case TerminalKey.Help => copy(screen = screen.withHelpPopup(show = true))
            case _ => this
          }

        case s: TerminalScreen.NoDifferences =>
          key match {
            case TerminalKey.Open =>
              s.state.reportRuns match {
                case Some(runs) =>
                  copy(
                    screen = TerminalScreen.ReportFinder(
                      FinderState.initial(runs, s.state.currentReportRunIndex, zoneId = zoneId),
                    ),
                  )
                case None =>
                  copy(screen = TerminalScreen.Terminated)
              }
            case TerminalKey.Help => copy(screen = screen.withHelpPopup(show = true))
            case _ => this
          }

        case s: TerminalScreen.Diff =>
          copy(screen = s.state.normalized.handleTerminalKey(key, height, zoneId))

        case _: TerminalScreen.ReportFinder | TerminalScreen.Terminated =>
          this
      }

    private def applyEscape(): InteractiveReportViewerState =
      screen match {
        case _: TerminalScreen.EmptyReport =>
          armOrExit()
        case s: TerminalScreen.NoDifferences =>
          s.state.reportRuns match {
            case Some(runs) =>
              copy(
                screen = TerminalScreen.ReportFinder(
                  FinderState.initial(runs, s.state.currentReportRunIndex, zoneId = zoneId),
                ),
                exitArmed = false,
              )
            case None =>
              armOrExit()
          }
        case s: TerminalScreen.Diff =>
          s.state.normalized.handleTerminalKey(TerminalKey.Escape, height, zoneId) match {
            case TerminalScreen.Terminated => armOrExit()
            case nextScreen => copy(screen = nextScreen, exitArmed = false)
          }
        case _: TerminalScreen.ReportFinder | TerminalScreen.Terminated =>
          this
      }

    private def armOrExit(): InteractiveReportViewerState =
      if (exitArmed) copy(screen = TerminalScreen.Terminated, exitArmed = false)
      else copy(exitArmed = true)

    private def applySearchKey(key: SearchKey): InteractiveReportViewerState =
      if (key == SearchKey.Quit) copy(screen = TerminalScreen.Terminated, exitArmed = false)
      else
        screen match {
          case s: TerminalScreen.ReportFinder =>
            key match {
              case SearchKey.Cancel =>
                s.state.returnTo match {
                  case Some(returnTo) => copy(screen = returnTo, exitArmed = false)
                  case None if s.state.query.nonEmpty =>
                    copy(
                      screen = TerminalScreen.ReportFinder(s.state.copy(query = "", selectedIndex = 0)),
                      exitArmed = false,
                    )
                  case None => armOrExit()
                }
              case _ => copy(screen = s.state.handleSearchKey(key), exitArmed = false)
            }
          case s: TerminalScreen.Diff if s.state.fieldSearch.active =>
            copy(screen = TerminalScreen.Diff(s.state.normalized.handleFieldSearchKey(key)), exitArmed = false)
          case _ =>
            this
        }

    private def parseTerminalKey(inputs: Vector[Int], index: Int): ParsedTerminalKey = {
      val code = inputs(index)
      if (code == 27) parseEscape(inputs, index)
      else {
        val key =
          keymap
            .lookupCode(code)
            .orElse(if (isPrintable(code)) keymap.lookupCharacter(code.toChar) else None)
            .getOrElse(TerminalKey.Ignored)
        ParsedTerminalKey(key, index + 1)
      }
    }

    private def parseEscape(inputs: Vector[Int], startIndex: Int): ParsedTerminalKey = {
      var sequence = Vector.empty[Int]
      var result = Option.empty[TerminalKey]
      var index = startIndex + 1
      var done = keymap.maxEscapeSequenceLength == 0

      while (!done && index < inputs.length && sequence.length < keymap.maxEscapeSequenceLength) {
        sequence = sequence :+ inputs(index)
        index += 1
        keymap.lookupEscape(sequence) match {
          case Some(key) =>
            result = Some(key)
            done = true
          case None =>
            done = !keymap.isEscapePrefix(sequence)
        }
      }

      result match {
        case Some(key) =>
          ParsedTerminalKey(key, index)
        case None if sequence.isEmpty =>
          ParsedTerminalKey(keymap.lookupCode(27).getOrElse(TerminalKey.Ignored), startIndex + 1)
        case None if sequence.headOption.contains(27) =>
          ParsedTerminalKey(keymap.lookupCode(27).getOrElse(TerminalKey.Ignored), startIndex + 1)
        case None =>
          ParsedTerminalKey(TerminalKey.Ignored, index)
      }
    }

    private def parseSearchKey(inputs: Vector[Int], index: Int): ParsedSearchKey = {
      val code = inputs(index)
      code match {
        case 3 | 4 =>
          ParsedSearchKey(SearchKey.Quit, index + 1)
        case 27 =>
          val parsed = parseEscape(inputs, index)
          val key =
            parsed.key match {
              case TerminalKey.Previous => SearchKey.Up
              case TerminalKey.Next => SearchKey.Down
              case TerminalKey.Escape => SearchKey.Cancel
              case TerminalKey.Ignored => SearchKey.Cancel
              case _ => SearchKey.Ignored
            }
          ParsedSearchKey(key, parsed.nextIndex)
        case 13 =>
          ParsedSearchKey(SearchKey.Submit, index + 1)
        case 10 =>
          ParsedSearchKey(SearchKey.Down, index + 1)
        case 11 =>
          ParsedSearchKey(SearchKey.Up, index + 1)
        case 21 =>
          ParsedSearchKey(SearchKey.Clear, index + 1)
        case 8 =>
          ParsedSearchKey(SearchKey.ToggleHierarchy, index + 1)
        case 127 =>
          ParsedSearchKey(SearchKey.Backspace, index + 1)
        case printable if isPrintable(printable) =>
          ParsedSearchKey(SearchKey.Character(printable.toChar), index + 1)
        case _ =>
          ParsedSearchKey(SearchKey.Ignored, index + 1)
      }
    }

    private def isPrintable(code: Int): Boolean =
      code >= 32 && code != 127
  }

  private def screenForRun(
    run: DiffRun,
    reportPanel: Option[ReportPanelSummary],
    reportRuns: Option[Vector[DiffRun]],
    currentReportRunIndex: Option[Int],
  ): TerminalScreen =
    if (run.changes.isEmpty)
      TerminalScreen.NoDifferences(NoDifferencesState(run, reportPanel, reportRuns, currentReportRunIndex))
    else TerminalScreen.Diff(DiffScreenState.initial(run, reportPanel, reportRuns, currentReportRunIndex))

  private[cli] object InteractiveReportViewerState {
    def initial(
      report: DiffReport,
      color: Boolean,
      width: Int,
      height: Int,
      keymap: TerminalKeymap = TerminalKeymap.default,
      initialIndex: Int = 0,
      zoneId: ZoneId = ZoneId.systemDefault(),
    ): InteractiveReportViewerState = {
      val screen =
        report.runs match {
          case Vector() =>
            TerminalScreen.EmptyReport()
          case Vector(run) if run.metadata.isEmpty =>
            screenForRun(run, reportPanel = None, reportRuns = None, currentReportRunIndex = None)
          case runs =>
            val initialRunIndex = if (initialIndex == 0) None else Some(normalizeIndex(initialIndex, runs.length))
            TerminalScreen.ReportFinder(FinderState.initial(runs, initialRunIndex, zoneId = zoneId))
        }
      InteractiveReportViewerState(color, width, height, keymap, zoneId, screen, exitArmed = false)
    }
  }

  private[cli] sealed trait TerminalScreen {
    def showHelpPopup: Boolean

    def withHelpPopup(show: Boolean): TerminalScreen =
      this match {
        case screen: TerminalScreen.EmptyReport => screen.copy(showHelpPopup = show)
        case screen: TerminalScreen.ReportFinder => screen.copy(showHelpPopup = show)
        case screen: TerminalScreen.Diff => screen.copy(showHelpPopup = show)
        case screen: TerminalScreen.NoDifferences => screen.copy(showHelpPopup = show)
        case TerminalScreen.Terminated => TerminalScreen.Terminated
      }
  }

  private[cli] object TerminalScreen {
    final case class EmptyReport(showHelpPopup: Boolean = false) extends TerminalScreen
    final case class ReportFinder(state: FinderState, showHelpPopup: Boolean = false) extends TerminalScreen
    final case class Diff(state: DiffScreenState, showHelpPopup: Boolean = false) extends TerminalScreen
    final case class NoDifferences(state: NoDifferencesState, showHelpPopup: Boolean = false) extends TerminalScreen
    case object Terminated extends TerminalScreen {
      override val showHelpPopup: Boolean = false
    }
  }

  private[cli] final case class NoDifferencesState(
    run: DiffRun,
    reportPanel: Option[ReportPanelSummary],
    reportRuns: Option[Vector[DiffRun]],
    currentReportRunIndex: Option[Int],
  )

  private[cli] final case class FinderState(
    runs: Vector[DiffRun],
    candidates: Vector[TestSearchCandidate],
    query: String,
    selectedIndex: Int,
    viewMode: TestSearchViewMode,
    returnTo: Option[TerminalScreen],
  ) {
    def handleSearchKey(key: SearchKey): TerminalScreen = {
      val matches = searchMatches(candidates, query)
      val clampedSelectedIndex = clampSearchIndex(selectedIndex, matches.length)

      key match {
        case SearchKey.Cancel =>
          returnTo.getOrElse(TerminalScreen.Terminated)

        case SearchKey.Quit =>
          TerminalScreen.Terminated

        case SearchKey.Submit =>
          matches.lift(clampedSelectedIndex) match {
            case Some(searchMatch) =>
              val runIndex = searchMatch.candidate.runIndex
              val run = runs(runIndex)
              screenForRun(
                run,
                reportPanel = Some(ReportPanelSummary.fromRun(run)),
                reportRuns = Some(runs),
                currentReportRunIndex = Some(runIndex),
              )
            case None =>
              returnTo.getOrElse(TerminalScreen.Terminated)
          }

        case SearchKey.Up =>
          TerminalScreen.ReportFinder(
            copy(selectedIndex = moveSearchSelection(clampedSelectedIndex, matches, viewMode, step = -1)),
          )

        case SearchKey.Down =>
          TerminalScreen.ReportFinder(
            copy(selectedIndex = moveSearchSelection(clampedSelectedIndex, matches, viewMode, step = 1)),
          )

        case SearchKey.Backspace =>
          if (query.nonEmpty) TerminalScreen.ReportFinder(copy(query = query.dropRight(1), selectedIndex = 0))
          else TerminalScreen.ReportFinder(copy(selectedIndex = clampedSelectedIndex))

        case SearchKey.Clear =>
          TerminalScreen.ReportFinder(copy(query = "", selectedIndex = 0))

        case SearchKey.ToggleHierarchy =>
          TerminalScreen.ReportFinder(copy(viewMode = viewMode.toggle, selectedIndex = clampedSelectedIndex))

        case SearchKey.Character(char) =>
          TerminalScreen.ReportFinder(copy(query = query + char, selectedIndex = 0))

        case SearchKey.Ignored =>
          TerminalScreen.ReportFinder(copy(selectedIndex = clampedSelectedIndex))
      }
    }
  }

  private[cli] object FinderState {
    def initial(
      runs: Vector[DiffRun],
      initialRunIndex: Option[Int],
      returnTo: Option[TerminalScreen] = None,
      zoneId: ZoneId = ZoneId.systemDefault(),
    ): FinderState = {
      val candidates = testSearchCandidates(runs, zoneId)
      val selectedIndex =
        initialRunIndex
          .flatMap { runIndex =>
            searchMatches(candidates, "").indexWhere(_.candidate.runIndex == runIndex) match {
              case -1 => None
              case index => Some(index)
            }
          }
          .getOrElse(0)

      FinderState(
        candidates = candidates,
        runs = runs,
        query = "",
        selectedIndex = selectedIndex,
        viewMode = TestSearchViewMode.Hierarchical,
        returnTo = returnTo,
      )
    }
  }

  private[cli] final case class DiffScreenState(
    currentRun: DiffRun,
    reportPanel: Option[ReportPanelSummary],
    reportRuns: Option[Vector[DiffRun]],
    currentReportRunIndex: Option[Int],
    typeNameDisplay: TypeNameDisplay,
    tree: DiffTree,
    anchor: Vector[String],
    expanded: Set[Vector[String]],
    explicitlyExpanded: Set[Vector[String]],
    selectedIndex: Int,
    fieldSearch: FieldSearchState,
  ) {
    def rows: Vector[DiffTreeRow] =
      tree.rows(anchor, expanded)

    def normalized: DiffScreenState = {
      val currentRows = rows
      copy(selectedIndex = normalizeIndex(selectedIndex, currentRows.length))
    }

    def handleTerminalKey(key: TerminalKey, height: Int, zoneId: ZoneId): TerminalScreen = {
      val state = normalized
      val currentRows = state.rows

      key match {
        case TerminalKey.Escape =>
          state.deanchorOneStep(zoneId)

        case TerminalKey.Quit =>
          TerminalScreen.Terminated

        case TerminalKey.Anchor =>
          val newAnchor = currentRows(state.selectedIndex).node.id
          TerminalScreen.Diff(
            state.copy(anchor = newAnchor, expanded = state.expanded + newAnchor, selectedIndex = 0),
          )

        case TerminalKey.Top =>
          TerminalScreen.Diff(
            DiffScreenState.initial(
              state.currentRun,
              state.reportPanel,
              state.reportRuns,
              state.currentReportRunIndex,
              state.typeNameDisplay,
            ),
          )

        case TerminalKey.Expand =>
          val node = currentRows(state.selectedIndex).node
          if (node.children.nonEmpty)
            TerminalScreen.Diff(
              state.copy(
                expanded = state.expanded + node.id,
                explicitlyExpanded = state.explicitlyExpanded + node.id,
              ),
            )
          else TerminalScreen.Diff(state)

        case TerminalKey.Collapse =>
          val node = currentRows(state.selectedIndex).node
          if (node.id == state.anchor) TerminalScreen.Diff(state)
          else if (state.expanded.contains(node.id))
            TerminalScreen.Diff(
              state.copy(
                expanded = state.expanded - node.id,
                explicitlyExpanded = state.explicitlyExpanded - node.id,
              ),
            )
          else {
            val nextSelectedIndex =
              state.tree
                .parentId(node.id)
                .flatMap { parent =>
                  currentRows.indexWhere(_.node.id == parent) match {
                    case -1 => None
                    case index => Some(index)
                  }
                }
                .getOrElse(state.selectedIndex)
            TerminalScreen.Diff(state.copy(selectedIndex = nextSelectedIndex))
          }

        case TerminalKey.ExpandLevel =>
          val ids = state.tree.expandableIdsInSubtree(currentRows(state.selectedIndex).node.id)
          TerminalScreen.Diff(
            state.copy(expanded = state.expanded ++ ids, explicitlyExpanded = state.explicitlyExpanded ++ ids),
          )

        case TerminalKey.CollapseLevel =>
          val ids = state.tree.expandableIdsInSubtree(currentRows(state.selectedIndex).node.id) - state.anchor
          TerminalScreen.Diff(
            state.copy(
              expanded = state.expanded -- ids,
              explicitlyExpanded = state.explicitlyExpanded -- ids,
            ),
          )

        case TerminalKey.Next =>
          TerminalScreen.Diff(state.copy(selectedIndex = nextIndex(state.selectedIndex, currentRows.length)))

        case TerminalKey.Previous =>
          TerminalScreen.Diff(state.copy(selectedIndex = previousIndex(state.selectedIndex)))

        case TerminalKey.NextDifference =>
          state.jumpToDifference(step = 1)

        case TerminalKey.PreviousDifference =>
          state.jumpToDifference(step = -1)

        case TerminalKey.FieldSearch =>
          TerminalScreen.Diff(state.copy(fieldSearch = state.fieldSearch.copy(active = true)))

        case TerminalKey.NextSearchResult =>
          TerminalScreen.Diff(state.jumpToFieldSearchMatch(state.fieldSearch.submittedQuery, step = 1))

        case TerminalKey.PreviousSearchResult =>
          TerminalScreen.Diff(state.jumpToFieldSearchMatch(state.fieldSearch.submittedQuery, step = -1))

        case TerminalKey.PageDown =>
          TerminalScreen.Diff(
            state.copy(selectedIndex = math.min(currentRows.length - 1, state.selectedIndex + pageStep(height))),
          )

        case TerminalKey.PageUp =>
          TerminalScreen.Diff(state.copy(selectedIndex = math.max(0, state.selectedIndex - pageStep(height))))

        case TerminalKey.Open =>
          val node = currentRows(state.selectedIndex).node
          if (node.children.nonEmpty)
            if (state.expanded.contains(node.id))
              TerminalScreen.Diff(
                state.copy(
                  expanded = state.expanded - node.id,
                  explicitlyExpanded = state.explicitlyExpanded - node.id,
                ),
              )
            else
              TerminalScreen.Diff(
                state.copy(
                  expanded = state.expanded + node.id,
                  explicitlyExpanded = state.explicitlyExpanded + node.id,
                ),
              )
          else TerminalScreen.Diff(state)

        case TerminalKey.ToggleTypeName =>
          val selectedId = currentRows(state.selectedIndex).node.id
          val nextTypeNameDisplay = state.typeNameDisplay.toggle
          val nextTree = DiffTree.fromResult(state.currentRun.result, nextTypeNameDisplay)
          val nextRows = nextTree.rows(state.anchor, state.expanded)
          TerminalScreen.Diff(
            state.copy(
              typeNameDisplay = nextTypeNameDisplay,
              tree = nextTree,
              selectedIndex = nextTree
                .rowIndex(selectedId, nextRows)
                .getOrElse(normalizeIndex(state.selectedIndex, nextRows.length)),
            ),
          )

        case TerminalKey.Search =>
          state.reportRuns match {
            case Some(runs) =>
              TerminalScreen.ReportFinder(
                FinderState.initial(
                  runs,
                  initialRunIndex = None,
                  returnTo = Some(TerminalScreen.Diff(state)),
                  zoneId = zoneId,
                ),
              )
            case None =>
              TerminalScreen.Diff(state)
          }

        case TerminalKey.Help =>
          TerminalScreen.Diff(state, showHelpPopup = true)

        case TerminalKey.Ignored =>
          TerminalScreen.Diff(state)
      }
    }

    def handleFieldSearchKey(key: SearchKey): DiffScreenState =
      key match {
        case SearchKey.Cancel =>
          copy(fieldSearch = fieldSearch.copy(query = "", active = false))

        case SearchKey.Quit =>
          this

        case SearchKey.Submit =>
          val submittedQuery = fieldSearch.query
          jumpToFieldSearchMatch(submittedQuery, step = 0)
            .copy(
              fieldSearch = fieldSearch.copy(
                query = "",
                active = false,
                submittedQuery = submittedQuery,
              ),
            )

        case SearchKey.Up =>
          jumpToFieldSearchMatch(fieldSearch.query, step = -1)

        case SearchKey.Down =>
          jumpToFieldSearchMatch(fieldSearch.query, step = 1)

        case SearchKey.Backspace =>
          if (fieldSearch.query.nonEmpty)
            copy(fieldSearch = fieldSearch.copy(query = fieldSearch.query.dropRight(1)))
          else this

        case SearchKey.Clear =>
          copy(fieldSearch = fieldSearch.copy(query = ""))

        case SearchKey.ToggleHierarchy =>
          this

        case SearchKey.Character(char) =>
          copy(fieldSearch = fieldSearch.copy(query = fieldSearch.query + char))

        case SearchKey.Ignored =>
          this
      }

    private def deanchorOneStep(zoneId: ZoneId): TerminalScreen =
      if (anchor == tree.root.id)
        reportRuns match {
          case Some(runs) =>
            TerminalScreen.ReportFinder(FinderState.initial(runs, currentReportRunIndex, zoneId = zoneId))
          case None =>
            TerminalScreen.Terminated
        }
      else {
        val previousAnchor = anchor
        tree.parentId(anchor) match {
          case Some(parent) =>
            val nextExpanded = expanded + parent
            val nextRows = tree.rows(parent, nextExpanded)
            TerminalScreen.Diff(
              copy(
                anchor = parent,
                expanded = nextExpanded,
                selectedIndex = tree.rowIndex(previousAnchor, nextRows).getOrElse(0),
              ),
            )
          case None =>
            TerminalScreen.Terminated
        }
      }

    private def selectedId: Vector[String] =
      rows.lift(selectedIndex).map(_.node.id).getOrElse(anchor)

    private def jumpToFieldSearchMatch(query: String, step: Int): DiffScreenState = {
      val matches = tree.fieldSearchIds(anchor, query)
      if (matches.isEmpty) this
      else {
        val currentIndex = matches.indexWhere(_ == selectedId)
        val targetIndex =
          if (step == 0)
            if (currentIndex >= 0) currentIndex else 0
          else if (currentIndex < 0)
            if (step > 0) 0 else matches.length - 1
          else
            (currentIndex + step + matches.length) % matches.length
        val targetId = matches(targetIndex)
        val nextExpanded = expanded ++ tree.ancestorIds(targetId)
        val nextRows = tree.rows(anchor, nextExpanded)
        copy(
          expanded = nextExpanded,
          selectedIndex = tree.rowIndex(targetId, nextRows).getOrElse(selectedIndex),
        )
      }
    }

    private def jumpToDifference(step: Int): TerminalScreen = {
      val currentRows = rows
      val currentId = currentRows(selectedIndex).node.id
      val target =
        if (step > 0) tree.nextDifferenceId(anchor, currentId, explicitlyExpanded)
        else tree.previousDifferenceId(anchor, currentId, explicitlyExpanded)

      target match {
        case Some(nodeId) =>
          val nextExpanded = expanded ++ tree.ancestorIds(nodeId)
          val nextRows = tree.rows(anchor, nextExpanded)
          val fallback =
            if (step > 0) (selectedIndex + 1) % currentRows.length
            else (selectedIndex + currentRows.length - 1) % currentRows.length
          TerminalScreen.Diff(
            copy(
              expanded = nextExpanded,
              selectedIndex = tree.rowIndex(nodeId, nextRows).getOrElse(fallback),
            ),
          )
        case None =>
          val nextSelectedIndex =
            if (step > 0) (selectedIndex + 1) % currentRows.length
            else (selectedIndex + currentRows.length - 1) % currentRows.length
          TerminalScreen.Diff(copy(selectedIndex = nextSelectedIndex))
      }
    }
  }

  private[cli] object DiffScreenState {
    def initial(
      run: DiffRun,
      reportPanel: Option[ReportPanelSummary],
      reportRuns: Option[Vector[DiffRun]],
      currentReportRunIndex: Option[Int],
      typeNameDisplay: TypeNameDisplay = TypeNameDisplay.Short,
    ): DiffScreenState = {
      val tree = DiffTree.fromResult(run.result, typeNameDisplay)
      val anchor = tree.root.id
      val expanded = tree.defaultExpanded
      val rows = tree.rows(anchor, expanded)
      DiffScreenState(
        currentRun = run,
        reportPanel = reportPanel,
        reportRuns = reportRuns,
        currentReportRunIndex = currentReportRunIndex,
        typeNameDisplay = typeNameDisplay,
        tree = tree,
        anchor = anchor,
        expanded = expanded,
        explicitlyExpanded = Set.empty,
        selectedIndex = tree.firstDifferenceRow(rows).getOrElse(0),
        fieldSearch = FieldSearchState.Empty,
      )
    }
  }

  private[cli] def runSession(
    report: DiffReport,
    session: TerminalSession,
    color: Boolean,
    initialIndex: Int = 0,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): Unit = {
    var model =
      InteractiveReportViewerState.initial(
        report = report,
        color = color,
        width = session.width,
        height = session.height,
        keymap = session.keymap,
        initialIndex = initialIndex,
        zoneId = zoneId,
      )

    while (!model.isTerminated) {
      session.render(model.render)
      model = model
        .resize(session.width, session.height)
        .handleInput(session.readInputBurst())
    }
  }

  private def renderDiffScreen(
    tree: DiffTree,
    anchor: Vector[String],
    rows: Vector[DiffTreeRow],
    fieldSearch: FieldSearchState,
    color: Boolean,
    selectedIndex: Int,
    width: Int,
    height: Int,
    reportPanel: Option[ReportPanelSummary],
  ): Vector[String] = {
    val selectorPanel = reportPanel.toVector.flatMap(summary => renderCollapsedReportPanel(summary, width))
    val header =
      if (anchor == tree.root.id) Vector.empty
      else {
        val anchorPath = tree.node(anchor).fold("$")(_.path.render)
        Vector(s"Viewing diff subtree from: $anchorPath", "")
      }
    val searchPanel = renderFieldSearchPanel(fieldSearch, tree, anchor, width)
    val listRows = math.max(1, height - selectorPanel.length - header.length - searchPanel.length)
    val start = windowStart(selectedIndex, rows.length, listRows)
    val visibleRows = rows.slice(start, start + listRows)
    val pathWidth = rowContentWidth(width)
    val highlightedSearchIds =
      if (fieldSearch.active) tree.fieldSearchIds(anchor, fieldSearch.query).toSet else Set.empty[Vector[String]]
    val visibleTreeIds = rows.iterator.map(_.node.id).toSet
    val descendantSearchCounts =
      if (fieldSearch.active) tree.fieldSearchHiddenDescendantCounts(anchor, fieldSearch.query, visibleTreeIds)
      else Map.empty[Vector[String], Int]
    val changeLines = visibleRows.zipWithIndex.map { case (row, offset) =>
      val index = start + offset
      val searchQuery =
        if (highlightedSearchIds.contains(row.node.id)) Some(fieldSearch.query) else None
      renderSelectableLine(
        renderDiffTreeRow(
          row,
          pathWidth,
          color,
          searchQuery,
          if (row.expanded) 0 else descendantSearchCounts.getOrElse(row.node.id, 0),
        ),
        selected = index == selectedIndex,
      )
    }

    val body = selectorPanel ++ header ++ changeLines
    body ++ Vector.fill(math.max(0, height - body.length - searchPanel.length))("") ++ searchPanel
  }

  private def renderFieldSearchPanel(
    fieldSearch: FieldSearchState,
    tree: DiffTree,
    anchor: Vector[String],
    width: Int,
  ): Vector[String] =
    if (!fieldSearch.isVisible) Vector.empty
    else {
      val noMatches =
        fieldSearch.query.trim.nonEmpty && tree.fieldSearchIds(anchor, fieldSearch.query).isEmpty
      val suffix = if (noMatches) " (no matches)" else ""
      renderBorderedPanel("Field search", Vector(s"/${fieldSearch.query}$suffix"), width)
    }

  private def renderCollapsedReportPanel(summary: ReportPanelSummary, width: Int): Vector[String] =
    renderBorderedPanel(
      "",
      Vector(
        s"Test: ${summary.testName}",
        s"Location: ${summary.location}",
      ),
      width,
    )

  private def renderBorderedPanel(
    title: String,
    lines: Vector[String],
    width: Int,
    borderStyle: BorderStyle = BorderStyle.Single,
  ): Vector[String] = {
    val panelWidth = math.max(4, width)
    val contentWidth = borderedPanelContentWidth(panelWidth)
    val top = panelBorder(borderStyle.topLeft, borderStyle.topRight, borderStyle.horizontal, title, panelWidth)
    val bottom = panelBorder(borderStyle.bottomLeft, borderStyle.bottomRight, borderStyle.horizontal, "", panelWidth)
    val body = lines.map { line =>
      val content = padRightVisible(fitAnsi(line, contentWidth), contentWidth)
      s"${borderStyle.vertical} $content ${borderStyle.vertical}"
    }

    top +: body :+ bottom
  }

  private final case class BorderStyle(
    topLeft: String,
    topRight: String,
    bottomLeft: String,
    bottomRight: String,
    horizontal: String,
    vertical: String,
  )

  private object BorderStyle {
    val Single: BorderStyle =
      BorderStyle("\u250c", "\u2510", "\u2514", "\u2518", "\u2500", "\u2502")

    val Double: BorderStyle =
      BorderStyle("\u2554", "\u2557", "\u255a", "\u255d", "\u2550", "\u2551")
  }

  private def panelBorder(left: String, right: String, horizontal: String, title: String, width: Int): String = {
    val plainBorder = left + (horizontal * (width - 2)) + right
    val label = fitPlain(title, math.max(0, width - 5))
    if (label.isEmpty) plainBorder
    else {
      val prefix = s"$left$horizontal $label "
      val remaining = width - prefix.length - right.length
      if (remaining <= 0) plainBorder
      else prefix + (horizontal * remaining) + right
    }
  }

  private def renderNoDifferencesScreen(
    run: DiffRun,
    color: Boolean,
    reportPanel: Option[ReportPanelSummary],
    width: Int,
  ): Vector[String] =
    reportPanel.toVector.flatMap(summary => renderCollapsedReportPanel(summary, width)) ++
      Vector("No differences.", "") ++
      renderConsoleDiff(run.result, color).linesIterator.toVector

  private def renderBottomMessage(lines: Vector[String], message: String, width: Int, height: Int): Vector[String] =
    if (height <= 0) Vector.empty
    else {
      val screen = lines.take(height) ++ Vector.fill(math.max(0, height - lines.length))("")
      screen.updated(height - 1, fitAnsi(message, math.max(0, width)))
    }

  private def renderConsoleDiff(result: DiffResult, color: Boolean): String = {
    val rendered = DiffResultPrinter.consoleOutput(result, 0).render
    if (color) rendered else Ansi.strip(rendered)
  }

  private def renderHelpPopup(
    baseScreen: Vector[String],
    width: Int,
    height: Int,
    keymap: TerminalKeymap,
  ): Vector[String] = {
    val helpLines = renderHelpLines(keymap)
    val popupWidth = centeredModalWidth(helpLines, width)
    val popup = renderBorderedPanel(
      "Hotkeys",
      helpLines,
      popupWidth,
    )
    renderCenteredModal(baseScreen, popup, width, height)
  }

  private def renderHelpLines(keymap: TerminalKeymap): Vector[String] = {
    val sections =
      Vector(
        "Navigation" -> Vector(
          helpLine(s"${keymap.previous.label} / ${keymap.next.label}", "move selection"),
          helpLine(s"${keymap.pageUp.label} / ${keymap.pageDown.label}", "move by half a screen"),
          helpLine(keymap.open.label, "open or toggle selected entry"),
          helpLine(s"${keymap.collapse.label} / ${keymap.expand.label}", "collapse or expand selected entry"),
          helpLine(
            s"${keymap.expandLevel.label} / ${keymap.collapseLevel.label}",
            "expand or collapse selected subtree",
          ),
        ),
        "Search" -> Vector(
          helpLine(keymap.fieldSearch.label, "search current view"),
          helpLine(keymap.search.label, "search tests with finder"),
        ),
        "Diff detail" -> Vector(
          helpLine(keymap.fieldSearch.label, "search field names"),
          helpLine(
            s"${keymap.nextSearchResult.label} / ${keymap.previousSearchResult.label}",
            "next or previous field search result",
          ),
          helpLine(s"${keymap.nextDifference.label} / ${keymap.previousDifference.label}", "next or previous diff"),
          helpLine(keymap.anchor.label, "anchor selected subtree"),
          helpLine(keymap.top.label, "return to top"),
          helpLine(keymap.toggleTypeName.label, "toggle short/full type names"),
        ),
        "General" -> Vector(
          helpLine(keymap.help.label, "show this help"),
          helpLine(keymap.escape.label, "go back or confirm quit"),
          helpLine(keymap.quit.label, "quit immediately"),
        ),
      )
    val keyLabelWidth = sections.flatMap { case (_, entries) => entries.map(_.keyLabel.length) }.maxOption.getOrElse(0)

    sections.zipWithIndex.flatMap { case ((title, entries), index) =>
      val sectionLines = title +: entries.map(_.render(keyLabelWidth))
      if (index == 0) sectionLines else "" +: sectionLines
    }
  }

  private final case class HelpLine(keyLabel: String, description: String) {
    def render(keyLabelWidth: Int): String = {
      val padding = " " * math.max(0, keyLabelWidth - keyLabel.length)
      s"  $keyLabel$padding  $description"
    }
  }

  private def helpLine(keyLabel: String, description: String): HelpLine =
    HelpLine(keyLabel, description)

  private def centeredModalWidth(lines: Vector[String], terminalWidth: Int): Int = {
    val desiredWidth = math.max(40, lines.map(visibleLength).maxOption.getOrElse(0) + 4)
    math.max(4, math.min(math.max(4, terminalWidth), desiredWidth))
  }

  private def renderCenteredModal(
    baseScreen: Vector[String],
    modal: Vector[String],
    width: Int,
    height: Int,
  ): Vector[String] = {
    val screenHeight = math.max(0, height)
    val screen =
      baseScreen.take(screenHeight) ++ Vector.fill(math.max(0, screenHeight - baseScreen.length))("")
    val top = math.max(0, (screenHeight - modal.length) / 2)
    val left = math.max(0, (width - modal.map(visibleLength).maxOption.getOrElse(0)) / 2)

    screen.zipWithIndex.map { case (line, index) =>
      val modalIndex = index - top
      if (modalIndex >= 0 && modalIndex < modal.length)
        (" " * left) + modal(modalIndex)
      else line
    }
  }

  private final case class SearchPromptState(query: String, selectedIndex: Int)

  private[cli] sealed trait TestSearchViewMode {
    def toggle: TestSearchViewMode
  }

  private[cli] object TestSearchViewMode {
    case object Flat extends TestSearchViewMode {
      override def toggle: TestSearchViewMode =
        Hierarchical
    }

    case object Hierarchical extends TestSearchViewMode {
      override def toggle: TestSearchViewMode =
        Flat
    }
  }

  private def renderSearchPrompt(
    state: SearchPromptState,
    matches: Vector[TestSearchMatch],
    viewMode: TestSearchViewMode,
    width: Int,
    height: Int,
    color: Boolean,
  ): Vector[String] = {
    val promptWidth = math.max(4, width)
    val contentWidth = rowContentWidth(borderedPanelContentWidth(promptWidth))
    val selectedIndex = clampSearchIndex(state.selectedIndex, matches.length)
    val selectedCandidate = matches.lift(selectedIndex).map(_.candidate)
    val matchLines =
      viewMode match {
        case TestSearchViewMode.Flat =>
          renderFlatSearchCandidateLines(matches, state.query, selectedIndex, contentWidth, color)
        case TestSearchViewMode.Hierarchical =>
          renderHierarchicalSearchCandidateLines(matches, state.query, selectedIndex, contentWidth, color)
      }
    val searchPanel = renderBorderedPanel(
      "Go to test",
      Vector(s"Search: ${state.query}", "") ++ matchLines,
      promptWidth,
      borderStyle = BorderStyle.Double,
    )
    val detailsPanel =
      renderBorderedPanel("", renderSearchCandidateDetails(selectedCandidate), promptWidth)
    val screen = searchPanel ++ detailsPanel
    screen.take(height) ++ Vector.fill(math.max(0, height - screen.length))("")
  }

  private def renderFlatSearchCandidateLines(
    matches: Vector[TestSearchMatch],
    query: String,
    selectedIndex: Int,
    contentWidth: Int,
    color: Boolean,
  ): Vector[String] = {
    val start = windowStart(selectedIndex, matches.length, SearchCandidateRows)
    val visibleMatches = matches.slice(start, start + SearchCandidateRows)
    val visibleLines =
      if (matches.isEmpty) Vector("No matches")
      else
        visibleMatches.zipWithIndex.map { case (searchMatch, offset) =>
          val index = start + offset
          renderSearchCandidateLine(
            searchMatch,
            query,
            selected = index == selectedIndex,
            contentWidth = contentWidth,
            color = color,
          )
        }
    visibleLines ++ Vector.fill(math.max(0, SearchCandidateRows - visibleLines.length))("")
  }

  private def renderHierarchicalSearchCandidateLines(
    matches: Vector[TestSearchMatch],
    query: String,
    selectedIndex: Int,
    contentWidth: Int,
    color: Boolean,
  ): Vector[String] = {
    if (matches.isEmpty)
      return Vector("No matches") ++ Vector.fill(math.max(0, SearchCandidateRows - 1))("")

    val lines = hierarchicalSearchRows(matches, query, selectedIndex, contentWidth, color)
    val selectedLineIndex = lines.indexWhere(_.selected)
    val start = windowStart(math.max(0, selectedLineIndex), lines.length, SearchCandidateRows)
    val visibleLines =
      lines.slice(start, start + SearchCandidateRows).map(_.line)
    visibleLines ++ Vector.fill(math.max(0, SearchCandidateRows - visibleLines.length))("")
  }

  private final case class SearchRenderRow(line: String, selected: Boolean)

  private final case class IndexedTestSearchMatch(index: Int, searchMatch: TestSearchMatch)

  private def moveSearchSelection(
    selectedIndex: Int,
    matches: Vector[TestSearchMatch],
    viewMode: TestSearchViewMode,
    step: Int,
  ): Int =
    viewMode match {
      case TestSearchViewMode.Flat =>
        clampSearchIndex(selectedIndex + step, matches.length)
      case TestSearchViewMode.Hierarchical =>
        val displayOrder = hierarchicalSearchMatchIndices(matches)
        if (displayOrder.isEmpty) 0
        else {
          val currentPosition = displayOrder.indexOf(selectedIndex) match {
            case -1 => 0
            case position => position
          }
          displayOrder(clampSearchIndex(currentPosition + step, displayOrder.length))
        }
    }

  private def hierarchicalSearchMatchIndices(matches: Vector[TestSearchMatch]): Vector[Int] =
    hierarchicalSearchMatchGroups(matches).flatMap { case (_, suiteGroups) =>
      suiteGroups.flatMap { case (_, suiteMatches) =>
        suiteMatches.map(_.index)
      }
    }

  private def hierarchicalSearchMatchGroups(
    matches: Vector[TestSearchMatch],
  ): Vector[(Option[String], Vector[(Option[String], Vector[IndexedTestSearchMatch])])] = {
    val indexedMatches = matches.zipWithIndex.map { case (searchMatch, index) =>
      IndexedTestSearchMatch(index, searchMatch)
    }
    val runIds = indexedMatches.map(_.searchMatch.candidate.runId).distinct
    runIds.map { runId =>
      val runMatches = indexedMatches.filter(_.searchMatch.candidate.runId == runId)
      val suiteNames = runMatches.map(_.searchMatch.candidate.suiteName).distinct
      val suiteGroups = suiteNames.map { suiteName =>
        suiteName -> runMatches.filter(_.searchMatch.candidate.suiteName == suiteName)
      }
      runId -> suiteGroups
    }
  }

  private def hierarchicalSearchRows(
    matches: Vector[TestSearchMatch],
    query: String,
    selectedIndex: Int,
    contentWidth: Int,
    color: Boolean,
  ): Vector[SearchRenderRow] = {
    val rows = Vector.newBuilder[SearchRenderRow]
    val runGroups = hierarchicalSearchMatchGroups(matches)

    runGroups.foreach { case (_, suiteGroups) =>
      val runMatches = suiteGroups.flatMap(_._2)
      val runCandidate = runMatches.head.searchMatch.candidate
      rows += SearchRenderRow(
        renderSearchFolderLine(
          runCandidate.runLabel,
          runMatches.exists(_.searchMatch.runIdMatch.nonEmpty),
          query,
          0,
          contentWidth,
          color,
        ),
        selected = false,
      )
      suiteGroups.foreach { case (_, suiteMatches) =>
        val suiteCandidate = suiteMatches.head.searchMatch.candidate
        rows += SearchRenderRow(
          renderSearchFolderLine(
            suiteCandidate.suiteLabel,
            suiteMatches.exists(_.searchMatch.suiteNameMatch.nonEmpty),
            query,
            1,
            contentWidth,
            color,
          ),
          selected = false,
        )
        suiteMatches.foreach { indexedSearchMatch =>
          rows += SearchRenderRow(
            renderHierarchicalSearchCandidateLine(
              indexedSearchMatch.searchMatch,
              query,
              selected = indexedSearchMatch.index == selectedIndex,
              contentWidth = contentWidth,
              color = color,
            ),
            selected = indexedSearchMatch.index == selectedIndex,
          )
        }
      }
    }

    rows.result()
  }

  private def renderSearchFolderLine(
    label: String,
    highlight: Boolean,
    query: String,
    depth: Int,
    contentWidth: Int,
    color: Boolean,
  ): String = {
    val marker = "\u25be "
    val prefix = "  " + ("  " * depth) + marker
    val segments = Vector(StyledText(prefix, None), StyledText(label, None))
    val highlighted =
      if (highlight) highlightSearchMatch(segments, Some(query)) else segments
    padRightVisible(fitAnsi(renderStyledSegments(highlighted, contentWidth, color), contentWidth), contentWidth)
  }

  private def renderHierarchicalSearchCandidateLine(
    searchMatch: TestSearchMatch,
    query: String,
    selected: Boolean,
    contentWidth: Int,
    color: Boolean,
  ): String = {
    val candidateSegments =
      Vector(StyledText("      ", None), StyledText(searchMatch.candidate.testName, None))
    val segments =
      highlightSearchMatch(candidateSegments, Some(query)) ++ testIdMatchBadge(searchMatch)
    val content = renderStyledSegments(segments, contentWidth, color)
    renderSelectableLine(content, selected)
  }

  private def renderSearchCandidateDetails(candidate: Option[TestSearchCandidate]): Vector[String] =
    candidate match {
      case Some(candidate) =>
        Vector(
          s"runId: ${candidate.runId.getOrElse("unknown")}",
          s"testId: ${candidate.testId.getOrElse("unknown")}",
          s"Suite: ${candidate.suiteName.getOrElse("unknown")}",
          s"Line: ${candidate.lineNumber.fold("unknown")(_.toString)}",
          s"File: ${candidate.filePath.getOrElse("unknown")}",
        )
      case None =>
        Vector(
          "runId: unknown",
          "testId: unknown",
          "Suite: unknown",
          "Line: unknown",
          "File: unknown",
        )
    }

  private def renderSearchCandidateLine(
    searchMatch: TestSearchMatch,
    query: String,
    selected: Boolean,
    contentWidth: Int,
    color: Boolean,
  ): String = {
    val candidate = searchMatch.candidate
    val timestamp = candidate.runTimestampLabel.toVector
    val candidateSegments =
      Vector(StyledText(candidate.testName, None)) ++
        timestamp.flatMap(label => Vector(StyledText(" ", None), StyledText(label, Some(RowColor.Pink))))
    val segments =
      highlightSearchMatch(candidateSegments, Some(query)) ++ testIdMatchBadge(searchMatch)
    val content = renderStyledSegments(segments, contentWidth, color)
    renderSelectableLine(content, selected)
  }

  private def testIdMatchBadge(searchMatch: TestSearchMatch): Vector[StyledText] =
    if (searchMatch.testIdMatch.nonEmpty)
      Vector(
        StyledText(" ", None),
        StyledText("[test id matches]", None, Some(TextHighlight.Search)),
      )
    else Vector.empty

  private def clampSearchIndex(index: Int, length: Int): Int =
    if (length <= 0) 0 else math.max(0, math.min(index, length - 1))

  private def testSearchCandidates(runs: Vector[DiffRun], zoneId: ZoneId): Vector[TestSearchCandidate] =
    runs.zipWithIndex.map { case (run, index) =>
      run.metadata match {
        case Some(metadata) =>
          val testName =
            if (metadata.testHierarchy.nonEmpty) metadata.testHierarchy.mkString(" / ") else metadata.testName
          val runTimestamp = runTimestampText(metadata.runId, zoneId)
          TestSearchCandidate(
            runIndex = index,
            testName = testName,
            runId = Some(metadata.runId),
            runTimestampLabel = Some(s"($runTimestamp)"),
            testId = Some(metadata.testId),
            suiteName = Some(metadata.suiteClassName.getOrElse(metadata.suiteName)),
            lineNumber = Some(metadata.lineNumber),
            filePath = Some(metadata.filePath),
          )
        case None =>
          TestSearchCandidate(
            runIndex = index,
            testName = "Raw comparison",
            runId = None,
            runTimestampLabel = None,
            testId = None,
            suiteName = None,
            lineNumber = None,
            filePath = None,
          )
      }
    }

  private[cli] def testSearchMatches(
    runs: Vector[DiffRun],
    query: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): Vector[TestSearchMatch] =
    searchMatches(testSearchCandidates(runs, zoneId), query)

  private def searchMatches(candidates: Vector[TestSearchCandidate], query: String): Vector[TestSearchMatch] = {
    val trimmed = query.trim
    if (trimmed.isEmpty)
      sortSearchMatches(candidates.map(candidate => TestSearchMatch(candidate, None, None, None, None)))
    else sortSearchMatches(fuzzySearchMatches(candidates, trimmed))
  }

  private def fuzzySearchMatches(
    candidates: Vector[TestSearchCandidate],
    query: String,
  ): Vector[TestSearchMatch] =
    candidates.flatMap { candidate =>
      val testNameMatch = FuzzySearch.find(candidate.testName, query)
      val suiteNameMatch = candidate.suiteName.flatMap(FuzzySearch.find(_, query))
      val runIdMatch = candidate.runId.flatMap(FuzzySearch.find(_, query))
      val testIdMatch = candidate.testId.flatMap(FuzzySearch.find(_, query))
      if (testNameMatch.nonEmpty || suiteNameMatch.nonEmpty || runIdMatch.nonEmpty || testIdMatch.nonEmpty)
        Some(TestSearchMatch(candidate, testNameMatch, suiteNameMatch, runIdMatch, testIdMatch))
      else None
    }

  private def sortSearchMatches(matches: Vector[TestSearchMatch]): Vector[TestSearchMatch] = {
    matches.zipWithIndex
      .sortWith { case ((leftMatch, leftIndex), (rightMatch, rightIndex)) =>
        val leftTimestamp = runTimestampMillis(leftMatch.candidate.runId)
        val rightTimestamp = runTimestampMillis(rightMatch.candidate.runId)
        val leftTestName = leftMatch.candidate.testName.toLowerCase
        val rightTestName = rightMatch.candidate.testName.toLowerCase

        if (leftTimestamp != rightTimestamp) leftTimestamp > rightTimestamp
        else if (leftMatch.score != rightMatch.score) leftMatch.score < rightMatch.score
        else if (leftTestName != rightTestName) leftTestName < rightTestName
        else leftIndex < rightIndex
      }
      .map { case (searchMatch, _) => searchMatch }
  }

  private def runTimestampMillis(runId: Option[String]): Long =
    runId.flatMap(Ulid.timestampMillis).getOrElse(Long.MinValue)

  private[cli] object FuzzySearch {
    final case class Match(score: Int, indices: Vector[Int])

    def matches(candidate: String, query: String): Boolean =
      find(candidate, query).nonEmpty

    def find(candidate: String, query: String): Option[Match] = {
      val haystack = candidate.toLowerCase
      val needle = query.toLowerCase.filterNot(_.isWhitespace)
      var previousIndex = -1
      var score = 0
      var needleIndex = 0
      val indices = Vector.newBuilder[Int]

      while (needleIndex < needle.length) {
        val char = needle.charAt(needleIndex)
        val nextIndex = haystack.indexOf(char.toInt, previousIndex + 1)
        if (nextIndex < 0) return None
        score += nextIndex - previousIndex - 1
        previousIndex = nextIndex
        indices += nextIndex
        needleIndex += 1
      }

      Some(Match(score, indices.result()))
    }
  }

  private def pageStep(height: Int): Int =
    math.max(1, height / 2)

  private def rowContentWidth(width: Int): Int =
    math.max(1, width - 3)

  private def borderedPanelContentWidth(width: Int): Int =
    math.max(1, width - 4)

  private def renderSelectableLine(content: String, selected: Boolean): String = {
    val left = if (selected) ">" else " "
    val right = if (selected) "<" else " "
    s"$left $content$right"
  }

  private def windowStart(selectedIndex: Int, totalRows: Int, visibleRows: Int): Int =
    if (visibleRows >= totalRows) 0
    else {
      val centered = selectedIndex - (visibleRows / 2)
      math.max(0, math.min(centered, totalRows - visibleRows))
    }

  private def normalizeIndex(index: Int, length: Int): Int =
    if (index >= 0 && index < length) index else 0

  private def nextIndex(index: Int, length: Int): Int =
    math.min(length - 1, index + 1)

  private def previousIndex(index: Int): Int =
    math.max(0, index - 1)

  private def fitPlain(value: String, width: Int): String = {
    val oneLine = value.replace('\n', ' ').replace('\r', ' ')
    if (width <= 0) ""
    else if (oneLine.length <= width) oneLine
    else oneLine.take(width)
  }

  private def visibleLength(value: String): Int =
    Ansi.strip(value).length

  private def padRight(value: String, width: Int): String =
    if (value.length >= width) value
    else value + (" " * (width - value.length))

  private def padRightVisible(value: String, width: Int): String = {
    val padding = width - visibleLength(value)
    if (padding <= 0) value else value + (" " * padding)
  }

  private def fitAnsi(value: String, width: Int): String = {
    val oneLineValue = oneLine(value)
    if (width <= 0) ""
    else if (visibleLength(oneLineValue) <= width) oneLineValue
    else {
      val builder = new StringBuilder(oneLineValue.length)
      var visible = 0
      var index = 0

      while (index < oneLineValue.length && visible < width) {
        if (
          oneLineValue
            .charAt(index) == '\u001b' && index + 1 < oneLineValue.length && oneLineValue.charAt(index + 1) == '['
        ) {
          val end = ansiSequenceEnd(oneLineValue, index + 2)
          if (end < oneLineValue.length) {
            builder.append(oneLineValue.substring(index, end + 1))
            index = end + 1
          } else {
            index = oneLineValue.length
          }
        } else {
          builder.append(oneLineValue.charAt(index))
          visible += 1
          index += 1
        }
      }

      if (oneLineValue.contains("\u001b[")) builder.append("\u001b[39m")
      builder.result()
    }
  }

  private def ansiSequenceEnd(value: String, start: Int): Int = {
    var index = start
    while (index < value.length && !isAnsiFinal(value.charAt(index))) index += 1
    index
  }

  private def isAnsiFinal(char: Char): Boolean =
    char >= '@' && char <= '~'

  private def runTimestampText(runId: String, zoneId: ZoneId): String =
    Ulid
      .timestampMillis(runId)
      .map(millis => timestampLabel(millis, Instant.now(), zoneId))
      .getOrElse("unknown time")

  private[cli] def timestampLabel(millis: Long, now: Instant, zoneId: ZoneId): String = {
    val timestamp = Instant.ofEpochMilli(millis).atZone(zoneId)
    val today = LocalDate.ofInstant(now, zoneId)
    val formatter =
      if (timestamp.toLocalDate == today) DateTimeFormatter.ofPattern("HH:mm:ss")
      else DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    timestamp.format(formatter)
  }

  private def renderDiffTreeRow(
    row: DiffTreeRow,
    width: Int,
    color: Boolean,
    searchQuery: Option[String],
    descendantSearchMatches: Int,
  ): String = {
    val rowSegments =
      renderDiffTreeSegments(row).getOrElse(Vector(StyledText(plainDiffTreeRow(row), diffTreeRowColor(row.node))))
    val highlightedSegments = highlightSearchMatch(rowSegments, searchQuery)
    renderStyledSegments(highlightedSegments ++ searchMatchBadge(descendantSearchMatches), width, color)
  }

  private def plainDiffTreeRow(row: DiffTreeRow): String = {
    val node = row.node
    val indent = "  " * row.depth
    val indicator =
      if (node.children.isEmpty && row.depth > 0) "  "
      else if (node.children.isEmpty) ""
      else if (row.expanded) "\u25be "
      else "\u25b8 "
    s"$indent$indicator${node.label}"
  }

  private def renderDiffTreeSegments(row: DiffTreeRow): Option[Vector[StyledText]] =
    row.node.result match {
      case result: DiffResult.ValueResult.Both if !result.isSame && !result.isIgnored =>
        val plain = oneLine(plainDiffTreeRow(row))
        val obtained = oneLine(result.obtained)
        val expected = oneLine(result.expected)
        val summary = s"$obtained -> $expected"
        val summaryStart = plain.lastIndexOf(summary)

        if (summaryStart < 0) None
        else
          Some(
            Vector(
              StyledText(plain.take(summaryStart), Some(RowColor.Purple)),
              StyledText(obtained, Some(RowColor.Red)),
              StyledText(" -> ", Some(RowColor.Purple)),
              StyledText(expected, Some(RowColor.Green)),
              StyledText(plain.drop(summaryStart + summary.length), Some(RowColor.Purple)),
            ),
          )

      case _ =>
        None
    }

  private def searchMatchBadge(count: Int): Vector[StyledText] =
    if (count <= 0) Vector.empty
    else {
      val noun = if (count == 1) "match" else "matches"
      Vector(
        StyledText("   ", None),
        StyledText(s"[$count $noun]", None, Some(TextHighlight.Search)),
      )
    }

  private def highlightSearchMatch(segments: Vector[StyledText], query: Option[String]): Vector[StyledText] =
    query.map(_.trim).filter(_.nonEmpty) match {
      case None => segments
      case Some(needle) =>
        val segmentTexts = segments.map(segment => oneLine(segment.value))
        FuzzySearch.find(segmentTexts.mkString, needle) match {
          case None => segments
          case Some(searchMatch) =>
            val highlightIndices = searchMatch.indices.toSet
            var offset = 0
            segments.zip(segmentTexts).flatMap { case (segment, text) =>
              val highlighted = splitStyledSegment(segment.copy(value = text), offset, highlightIndices)
              offset += text.length
              highlighted
            }
        }
    }

  private def splitStyledSegment(
    segment: StyledText,
    segmentStart: Int,
    highlightIndices: Set[Int],
  ): Vector[StyledText] = {
    val text = segment.value
    val localIndices = highlightIndices
      .collect {
        case index if index >= segmentStart && index < segmentStart + text.length => index - segmentStart
      }
      .toVector
      .sorted

    if (localIndices.isEmpty) Vector(segment)
    else {
      val ranges = contiguousRanges(localIndices)
      val result = Vector.newBuilder[StyledText]
      var cursor = 0

      ranges.foreach { case (start, end) =>
        if (cursor < start) result += segment.copy(value = text.slice(cursor, start))
        result += segment.copy(value = text.slice(start, end), highlight = Some(TextHighlight.Search))
        cursor = end
      }

      if (cursor < text.length) result += segment.copy(value = text.drop(cursor))
      result.result().filter(_.value.nonEmpty)
    }
  }

  private def contiguousRanges(indices: Vector[Int]): Vector[(Int, Int)] =
    if (indices.isEmpty) Vector.empty
    else {
      val ranges = Vector.newBuilder[(Int, Int)]
      var start = indices.head
      var previous = indices.head

      indices.tail.foreach { index =>
        if (index == previous + 1) previous = index
        else {
          ranges += ((start, previous + 1))
          start = index
          previous = index
        }
      }

      ranges += ((start, previous + 1))
      ranges.result()
    }

  private def renderStyledSegments(segments: Vector[StyledText], width: Int, color: Boolean): String = {
    val builder = new StringBuilder
    var remaining = width

    segments.foreach { segment =>
      if (remaining > 0) {
        val text = oneLine(segment.value)
        val visible = if (text.length <= remaining) text else text.take(remaining)
        if (visible.nonEmpty) {
          val styled = colorText(visible, segment.color, color)
          val highlighted =
            segment.highlight match {
              case Some(TextHighlight.Search) => Ansi.searchHighlight(styled, color)
              case None => styled
            }
          builder.append(highlighted)
        }
        remaining -= visible.length
      }
    }

    if (remaining > 0) builder.append(" " * remaining)

    builder.result()
  }

  private def oneLine(value: String): String =
    value.replace('\n', ' ').replace('\r', ' ')

  private def diffTreeRowColor(node: DiffTreeNode): Option[RowColor] =
    if (node.result.isIgnored) Some(RowColor.LightGrey)
    else
      node.result.pairType match {
        case PairType.ObtainedOnly => Some(RowColor.Red)
        case PairType.ExpectedOnly => Some(RowColor.Green)
        case PairType.Both if !node.result.isOk => Some(RowColor.Purple)
        case PairType.Both => None
      }

  private def colorText(value: String, rowColor: Option[RowColor], color: Boolean): String =
    rowColor match {
      case Some(RowColor.Red) => Ansi.red(value, color)
      case Some(RowColor.Pink) => Ansi.purple(value, color)
      case Some(RowColor.Purple) => Ansi.purple(value, color)
      case Some(RowColor.Green) => Ansi.green(value, color)
      case Some(RowColor.LightGrey) => Ansi.lightGrey(value, color)
      case None => value
    }

  private final case class StyledText(value: String, color: Option[RowColor], highlight: Option[TextHighlight] = None)

  private sealed trait TextHighlight

  private object TextHighlight {
    case object Search extends TextHighlight
  }

  private sealed trait RowColor

  private object RowColor {
    case object Red extends RowColor
    case object Pink extends RowColor
    case object Purple extends RowColor
    case object Green extends RowColor
    case object LightGrey extends RowColor
  }

  private[cli] sealed trait TypeNameDisplay {
    def label: String
    def toggle: TypeNameDisplay
  }

  private[cli] object TypeNameDisplay {
    case object Short extends TypeNameDisplay {
      override val label: String = "short"
      override def toggle: TypeNameDisplay = Full
    }

    case object Full extends TypeNameDisplay {
      override val label: String = "full"
      override def toggle: TypeNameDisplay = Short
    }
  }

  private[cli] final case class ReportPanelSummary(testName: String, location: String)

  private[cli] object ReportPanelSummary {
    def fromRun(run: DiffRun): ReportPanelSummary =
      run.metadata match {
        case Some(metadata) =>
          val suiteName = metadata.suiteClassName.getOrElse(metadata.suiteName)
          val testName =
            if (metadata.testHierarchy.nonEmpty) metadata.testHierarchy.mkString(" / ") else metadata.testName
          val fileName = if (metadata.fileName.nonEmpty) metadata.fileName else metadata.filePath
          ReportPanelSummary(s"$suiteName / $testName", s"$fileName:${metadata.lineNumber}")
        case None =>
          ReportPanelSummary("Raw comparison", "unknown location")
      }
  }

  private[cli] final case class TestSearchCandidate(
    runIndex: Int,
    testName: String,
    runId: Option[String],
    runTimestampLabel: Option[String],
    testId: Option[String],
    suiteName: Option[String],
    lineNumber: Option[Int],
    filePath: Option[String],
  ) {
    def runLabel: String =
      runId match {
        case Some(runId) => s"$runId ${runTimestampLabel.getOrElse("")}".trim
        case None => "Raw comparison"
      }

    def suiteLabel: String =
      suiteName.getOrElse("unknown suite")
  }

  private[cli] final case class TestSearchMatch(
    candidate: TestSearchCandidate,
    testNameMatch: Option[FuzzySearch.Match],
    suiteNameMatch: Option[FuzzySearch.Match],
    runIdMatch: Option[FuzzySearch.Match],
    testIdMatch: Option[FuzzySearch.Match],
  ) {
    def score: Int =
      Vector(testNameMatch, suiteNameMatch, runIdMatch, testIdMatch).flatten.map(_.score).minOption.getOrElse(0)
  }

  private[cli] final case class FieldSearchState(query: String, active: Boolean, submittedQuery: String) {
    def isVisible: Boolean =
      active
  }

  private[cli] object FieldSearchState {
    val Empty: FieldSearchState =
      FieldSearchState(query = "", active = false, submittedQuery = "")
  }

  private[cli] final case class DiffTreeRow(node: DiffTreeNode, depth: Int, expanded: Boolean)

  private[cli] final case class DiffTreeNode(
    id: Vector[String],
    path: DiffPath,
    label: String,
    searchName: Option[String],
    result: DiffResult,
    children: Vector[DiffTreeNode],
  ) {
    def isDifference: Boolean =
      !result.isIgnored && !result.isOk && (children.isEmpty || result.pairType != PairType.Both)

    def isSideOnlyContainer: Boolean =
      children.nonEmpty &&
        !result.isInstanceOf[DiffResult.ValueResult] &&
        (result.pairType == PairType.ObtainedOnly || result.pairType == PairType.ExpectedOnly)

  }

  private[cli] final case class DiffTree(root: DiffTreeNode) {
    private val nodeById: Map[Vector[String], DiffTreeNode] =
      flatten(root).map(node => node.id -> node).toMap

    val defaultExpanded: Set[Vector[String]] =
      firstDifference(root).toVector.flatMap(node => ancestors(node.id)).toSet + root.id

    def node(id: Vector[String]): Option[DiffTreeNode] =
      nodeById.get(id)

    def parentId(id: Vector[String]): Option[Vector[String]] =
      if (id.isEmpty) None else Some(id.dropRight(1))

    def rows(anchor: Vector[String], expanded: Set[Vector[String]]): Vector[DiffTreeRow] =
      node(anchor).toVector.flatMap(visibleRows(_, expanded, depth = 0))

    def firstDifferenceRow(rows: Vector[DiffTreeRow]): Option[Int] =
      differenceRow(rows, -1, step = 1)

    def rowIndex(id: Vector[String], rows: Vector[DiffTreeRow]): Option[Int] =
      rows.indexWhere(_.node.id == id) match {
        case -1 => None
        case index => Some(index)
      }

    def ancestorIds(id: Vector[String]): Set[Vector[String]] =
      ancestors(id).toSet

    def expandableIdsInSubtree(id: Vector[String]): Set[Vector[String]] =
      node(id)
        .fold(Vector.empty[DiffTreeNode])(flatten)
        .collect { case subtreeNode if subtreeNode.children.nonEmpty => subtreeNode.id }
        .toSet

    def fieldSearchIds(anchor: Vector[String], query: String): Vector[Vector[String]] = {
      val normalized = query.trim.toLowerCase
      if (normalized.isEmpty) Vector.empty
      else
        node(anchor).toVector.flatMap { anchorNode =>
          flatten(anchorNode).collect {
            case node if node.searchName.exists(name => FuzzySearch.matches(name, normalized)) => node.id
          }
        }
    }

    def fieldSearchHiddenDescendantCounts(
      anchor: Vector[String],
      query: String,
      visibleIds: Set[Vector[String]],
    ): Map[Vector[String], Int] =
      fieldSearchIds(anchor, query)
        .filterNot(visibleIds)
        .foldLeft(Map.empty[Vector[String], Int]) { case (counts, matchId) =>
          ancestorIds(matchId).foldLeft(counts) { case (innerCounts, ancestorId) =>
            nodeById.get(ancestorId) match {
              case Some(ancestorNode) if ancestorNode.children.nonEmpty =>
                innerCounts.updated(ancestorId, innerCounts.getOrElse(ancestorId, 0) + 1)
              case _ =>
                innerCounts
            }
          }
        }

    def nextDifferenceId(
      anchor: Vector[String],
      selectedId: Vector[String],
      explicitlyExpanded: Set[Vector[String]],
    ): Option[Vector[String]] =
      differenceId(anchor, selectedId, explicitlyExpanded, step = 1)

    def previousDifferenceId(
      anchor: Vector[String],
      selectedId: Vector[String],
      explicitlyExpanded: Set[Vector[String]],
    ): Option[Vector[String]] =
      differenceId(anchor, selectedId, explicitlyExpanded, step = -1)

    private def differenceId(
      anchor: Vector[String],
      selectedId: Vector[String],
      explicitlyExpanded: Set[Vector[String]],
      step: Int,
    ): Option[Vector[String]] =
      node(anchor).flatMap { anchorNode =>
        val nodes = differenceNavigationNodes(anchorNode, explicitlyExpanded)
        if (nodes.isEmpty) None
        else {
          val length = nodes.length
          val selectedIndex = nodes.indexWhere(_.id == selectedId) match {
            case -1 => 0
            case index => index
          }

          Iterator
            .iterate((selectedIndex + step + length) % length)(index => (index + step + length) % length)
            .take(length)
            .find(index => nodes(index).isDifference)
            .map(index => nodes(index).id)
        }
      }

    private def differenceNavigationNodes(
      node: DiffTreeNode,
      explicitlyExpanded: Set[Vector[String]],
    ): Vector[DiffTreeNode] = {
      val children =
        if (node.isSideOnlyContainer && !explicitlyExpanded.contains(node.id)) Vector.empty
        else node.children.flatMap(child => differenceNavigationNodes(child, explicitlyExpanded))
      node +: children
    }

    private def differenceRow(rows: Vector[DiffTreeRow], selectedIndex: Int, step: Int): Option[Int] =
      if (rows.isEmpty) None
      else {
        val length = rows.length
        Iterator
          .iterate((selectedIndex + step + length) % length)(index => (index + step + length) % length)
          .take(length)
          .find(index => rows(index).node.isDifference)
      }

    private def visibleRows(
      node: DiffTreeNode,
      expanded: Set[Vector[String]],
      depth: Int,
    ): Vector[DiffTreeRow] = {
      val isExpanded = expanded.contains(node.id)
      val row = DiffTreeRow(node, depth, isExpanded)
      if (isExpanded) row +: node.children.flatMap(child => visibleRows(child, expanded, depth + 1))
      else Vector(row)
    }

    private def firstDifference(node: DiffTreeNode): Option[DiffTreeNode] =
      if (node.isDifference) Some(node)
      else node.children.iterator.flatMap(firstDifference).toSeq.headOption

    private def ancestors(id: Vector[String]): Vector[Vector[String]] =
      id.indices.map(index => id.take(index)).toVector

    private def flatten(node: DiffTreeNode): Vector[DiffTreeNode] =
      node +: node.children.flatMap(flatten)

  }

  private[cli] object DiffTree {
    def fromResult(result: DiffResult, typeNameDisplay: TypeNameDisplay): DiffTree =
      DiffTree(
        build(
          result,
          DiffPath.root,
          Vector.empty,
          rootLabel(result, typeNameDisplay),
          searchName = None,
          typeNameDisplay,
        ),
      )

    private def build(
      result: DiffResult,
      path: DiffPath,
      id: Vector[String],
      label: String,
      searchName: Option[String],
      typeNameDisplay: TypeNameDisplay,
    ): DiffTreeNode = {
      val children =
        if (result.isIgnored) Vector.empty
        else
          result match {
            case result: DiffResult.ListResult =>
              result.items.zipWithIndex.map { case (item, index) =>
                build(
                  item,
                  path / DiffPath.Index(index),
                  id :+ s"[$index]",
                  s"[$index]: ${summaryLabel(item, typeNameDisplay)}",
                  searchName = None,
                  typeNameDisplay,
                )
              }
            case result: DiffResult.RecordResult =>
              val fieldNameWidth = result.fields.keysIterator.map(_.length).maxOption.getOrElse(0)
              result.fields.toVector.map { case (fieldName, value) =>
                val paddedFieldName = padRight(fieldName, fieldNameWidth)
                build(
                  value,
                  path / DiffPath.Field(fieldName),
                  id :+ fieldName,
                  s"$paddedFieldName: ${summaryLabel(value, typeNameDisplay)}",
                  searchName = Some(fieldName),
                  typeNameDisplay,
                )
              }
            case result: DiffResult.MapResult =>
              result.entries.map { case Entry(key, value) =>
                val decoded = decodeMapKey(key)
                build(
                  value,
                  path / DiffPath.Field(decoded),
                  id :+ decoded,
                  s"$decoded -> ${summaryLabel(value, typeNameDisplay)}",
                  searchName = Some(decoded),
                  typeNameDisplay,
                )
              }
            case _ =>
              Vector.empty
          }

      DiffTreeNode(id, path, label, searchName, result, children)
    }

    private def rootLabel(result: DiffResult, typeNameDisplay: TypeNameDisplay): String =
      summaryLabel(result, typeNameDisplay)

    private def summaryLabel(result: DiffResult, typeNameDisplay: TypeNameDisplay): String =
      result match {
        case result: DiffResult.ListResult => formatTypeName(result.typeName, typeNameDisplay)
        case result: DiffResult.RecordResult => formatTypeName(result.typeName, typeNameDisplay)
        case result: DiffResult.MapResult => formatTypeName(result.typeName, typeNameDisplay)
        case result: DiffResult.MismatchTypeResult =>
          s"${formatTypeName(result.obtainedTypeName, typeNameDisplay)} != ${formatTypeName(result.expectedTypeName, typeNameDisplay)}"
        case DiffResult.ValueResult.Both(obtained, expected, isSame, _) =>
          if (isSame) obtained else s"$obtained -> $expected"
        case DiffResult.ValueResult.ObtainedOnly(obtained, _) =>
          s"+ $obtained"
        case DiffResult.ValueResult.ExpectedOnly(expected, _) =>
          s"- $expected"
      }

    private def decodeMapKey(key: String): String =
      decode[String](key).getOrElse(key)
  }

  private def formatTypeName(typeName: TypeName.SomeTypeName, typeNameDisplay: TypeNameDisplay): String = {
    val base =
      typeNameDisplay match {
        case TypeNameDisplay.Short => typeName.short
        case TypeNameDisplay.Full => typeName.long
      }
    if (typeName.typeArguments.isEmpty) base
    else typeName.typeArguments.map(formatTypeName(_, typeNameDisplay)).mkString(s"$base[", ",", "]")
  }

  private final class JLineTerminalSession(
    terminal: Terminal,
    override val keymap: TerminalKeymap,
  ) extends TerminalSession {
    override def width: Int =
      math.max(20, terminal.getWidth)

    override def height: Int =
      math.max(6, terminal.getHeight)

    override def readInputBurst(): Vector[Int] = {
      val reader = terminal.reader()
      val code = reader.read()
      if (code == NonBlockingReader.EOF) keymap.terminalKeyInputs(TerminalKey.Quit)
      else if (code != 27) Vector(code)
      else {
        var sequence = Vector.empty[Int]
        var done = keymap.maxEscapeSequenceLength == 0

        while (!done && sequence.length < keymap.maxEscapeSequenceLength) {
          reader.read(25L) match {
            case NonBlockingReader.READ_EXPIRED | NonBlockingReader.EOF =>
              done = true

            case nextCode =>
              sequence = sequence :+ nextCode
              done = keymap.lookupEscape(sequence).nonEmpty || !keymap.isEscapePrefix(sequence)
          }
        }

        27 +: sequence
      }
    }

    override def render(lines: Vector[String]): Unit = {
      val writer = terminal.writer()
      writer.print("\u001b[H\u001b[2J")
      val visibleLines = lines.take(height)
      visibleLines.zipWithIndex.foreach { case (line, index) =>
        writer.print(truncateAnsi(line, width))
        writer.print("\u001b[K")
        if (index < visibleLines.length - 1)
          writer.print("\r\n")
      }
      terminal.flush()
    }

    def enterFullscreen(): Unit = {
      val writer = terminal.writer()
      writer.print("\u001b[?1049h\u001b[?25l\u001b[H\u001b[2J")
      terminal.flush()
    }

    def exitFullscreen(): Unit = {
      val writer = terminal.writer()
      writer.print("\u001b[?25h\u001b[?1049l")
      terminal.flush()
    }

    private def truncateAnsi(value: String, width: Int): String = {
      val builder = new StringBuilder(value.length)
      var visible = 0
      var index = 0

      while (index < value.length && visible < width) {
        if (value.charAt(index) == '\u001b' && index + 1 < value.length && value.charAt(index + 1) == '[') {
          val end = ansiSequenceEnd(value, index + 2)
          if (end < value.length) {
            builder.append(value.substring(index, end + 1))
            index = end + 1
          } else {
            index = value.length
          }
        } else {
          builder.append(value.charAt(index))
          visible += 1
          index += 1
        }
      }

      builder.result()
    }

    private def ansiSequenceEnd(value: String, start: Int): Int = {
      var index = start
      while (index < value.length && !isAnsiFinal(value.charAt(index))) index += 1
      index
    }

    private def isAnsiFinal(char: Char): Boolean =
      char >= '@' && char <= '~'
  }
}

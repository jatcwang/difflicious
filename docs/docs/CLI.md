---
id: cli
title: Diff Viewer UI / CLI
sidebar_label: Diff Viewer UI / CLI
---

# Diff Viewer UI / CLI

Difflicious CLI provides both interactive and non-interactive viewing of diffs generated
by the tests failures.

You can interactively explore diffs from test failures by launching in TUI (Terminal user interface) mode.
For non-interactive use cases such as for LLM AIs, you can view the diffs in **plain** (human readable) or **JSON** format.

If you are using difflicious' sbt plugin `sbt-difflicious` then you can run the `diffliciousViewer` command to start the TUI / CLI.

## Interactive TUI

By default, the CLI launches in TUI (Terminal User Interface) mode. You can search for tests and interactively explore the differences.

```
sbt> diffliciousViewer
```

<p><b>Search for and select a test failure:</b></p>
![](assets/cli-search.png)

<p><b>Explore the selected diff:</b></p>
![](assets/cli-details.png)

### Hotkeys

Here are some TUI hotkeys. In general, vim-style keybindings are provided too.

| Hotkey | Action |
| --- | --- |
| <kbd>↑</kbd> / <kbd>k</kbd>, <kbd>↓</kbd> / <kbd>j</kbd> | Move the selection up or down |
| <kbd>Enter</kbd> / <kbd>o</kbd> | Open or toggle the selected entry |
| <kbd>←</kbd> / <kbd>h</kbd>, <kbd>→</kbd> / <kbd>l</kbd> | Collapse or expand the selected field |
| <kbd>f</kbd> / <kbd>b</kbd> | Jump to the next or previous difference |
| <kbd>/</kbd> | Search field names in the current diff |
| <kbd>n</kbd> / <kbd>N</kbd> | Jump to the next or previous search result |
| <kbd>Ctrl</kbd>+<kbd>P</kbd> | Open the test search window |
| <kbd>a</kbd> | Anchor the selected subtree as the root. Useful if you want to "zoom in" on a particular part of the diff) |
| <kbd>t</kbd> | Reset anchoring and return to showing the root of the diff |
| <kbd>?</kbd> / <kbd>F1</kbd> | Show the complete hotkey reference |
| <kbd>Esc</kbd> | Go back or confirm quit |
| <kbd>Ctrl</kbd>+<kbd>C</kbd> / <kbd>Ctrl</kbd>+<kbd>D</kbd> | Quit immediately |

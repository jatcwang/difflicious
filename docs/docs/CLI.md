---
id: cli
title: CLI
sidebar_label: CLI
---

# CLI

Difflicious includes a CLI for viewing JSONL diff reports produced by the
ScalaTest listener. It can render the same failures as plain text, JSON,
or an interactive terminal UI.

The command shape is:

```bash
difflicious [options] <report.jsonl | report-directory>...
```

Until a packaged binary is published, run it from this repository with sbt:

```bash
sbt --client "cli/run --plain target/difflicious-scalatest-diffs"
```

## Inputs

The primary input is the JSONL written by
`difflicious.scalatest.DiffResultJsonlReporter`. The reporter writes one JSONL
file per ScalaTest suite and defaults to:

```text
target/difflicious-result
```

Add the sbt plugin to `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.jatcwang" % "sbt-difflicious" % "@VERSION@")
```

The plugin is enabled automatically. It registers the ScalaTest JSONL reporter
and passes a fresh `difflicious.runId` plus the JSONL output directory through
`javaOptions`. For non-forked tests, it also mirrors those values into the sbt
JVM system properties before `Test / test` runs.

Set `Test / diffliciousReportOutputDir` to change the report directory. Its
default is `target/difflicious-result` within each platform target.

Set `ThisBuild / diffliciousOverrideRunId := Some("...")` to override the
generated run id for a build. Otherwise, `ThisBuild / diffliciousCurrentRunId`
creates a fresh run id for each test command while sharing it across modules in
the same run.

If you are not using the plugin, enable the reporter in sbt with ScalaTest's
custom reporter option:

```scala
Test / testOptions += Tests.Argument(
  TestFrameworks.ScalaTest,
  "-C",
  "difflicious.scalatest.DiffResultJsonlReporter",
)
```

The output directory can be changed by setting this JVM system property before
the tests run:

```text
difflicious.report.outputDir
```

Pass a single JSONL file:

```bash
difflicious target/difflicious-scalatest-diffs/example.ExampleSuite.jsonl
```

Or pass the whole reporter directory:

```bash
difflicious target/difflicious-scalatest-diffs
```

Multiple files and directories can be passed together:

```bash
difflicious suite-a.jsonl suite-b.jsonl target/other-diffs
```

Filter report output to a single test with `--test-id`:

```bash
difflicious --plain --test-id "01ARZ3NDEKTSV4RRFFQ69G5FAW" target/difflicious-scalatest-diffs
```

The ScalaTest reporter writes a fresh ULID `testId` for each diff failure.
Plain, TUI, and JSON output show the id when it is present;
JSON exposes it as `metadata.testId`. In TUI mode, `--test-id` keeps
the full report loaded and starts with the matching failure selected.

Use `-` to read JSONL from standard input in non-interactive modes:

```bash
cat target/difflicious-scalatest-diffs/example.ExampleSuite.jsonl | difflicious --plain -
```

TUI mode cannot read JSONL from standard input because it reserves standard
input for interactive commands.

## Playground Failure

This repository includes an opt-in failing ScalaTest suite for trying the CLI:

```bash
sbt --client "cliPlayground/test"
```

The playground module is not part of the normal aggregate test set. The command
above intentionally fails and writes a JSONL report to the default reporter
directory:

```text
target/difflicious-scalatest-diffs
```

Open that report with any CLI mode:

```bash
sbt --client "cli/run --plain target/difflicious-scalatest-diffs"
sbt --client "cli/run --interactive target/difflicious-scalatest-diffs"
```

## Output Modes

TUI mode is the default when no output mode is selected.

Machine JSON output is intended for scripts and other tools:

```bash
difflicious --json target/difflicious-scalatest-diffs
```

Plain output is text with a summary and a numbered change list:

```bash
difflicious --plain target/difflicious-scalatest-diffs
```

TUI mode opens an interactive terminal viewer:

```bash
difflicious --interactive target/difflicious-scalatest-diffs
```

`--interactive`, `--json`, and `--plain` are mutually exclusive. When none is
specified, interactive mode is used.

## TUI Commands

Inside TUI mode, use these commands:

| Command | Action |
| --- | --- |
| `enter`, `o`, `open` | Open the selected failure |
| `n`, `next`, `j` | Move to the next failure |
| `p`, `prev`, `previous`, `k` | Move to the previous failure |
| `1`, `2`, ... | Jump to a numbered failure |
| `q`, `quit`, `exit` | Quit |

When a failure is open, these commands navigate that failure's individual
changes:

| Command | Action |
| --- | --- |
| `enter`, `n`, `next`, `j` | Move to the next change |
| `p`, `prev`, `previous`, `k` | Move to the previous change |
| `1`, `2`, ... | Jump to a numbered change |
| `a`, `all` | Print the full diff |
| `q`, `quit`, `exit` | Return to the failure list, or quit when only one failure is loaded |

## Color

TUI mode uses ANSI color by default. Disable color with:

```bash
difflicious --no-color target/difflicious-scalatest-diffs
```

Machine JSON and plain output are uncolored.

## Exit Codes

| Code | Meaning |
| --- | --- |
| `0` | Output was rendered successfully, including diff failures |
| `2` | Invalid arguments, unreadable input, invalid JSONL, or unsupported stdin usage |

Check the rendered output to decide whether differences were present:

```bash
difflicious --json target/difflicious-scalatest-diffs > diff.json
jq -e '.isOk == false' diff.json >/dev/null && echo "Diff failures written to diff.json"
```

## Help

Print the generated help text with:

```bash
difflicious --help
difflicious -h
```

From the repository:

```bash
sbt --client "cli/run --help"
```

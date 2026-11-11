---
id: zio-test
title: ZIO Test
---

# ZIO Test

Add the Difflicious sbt plugin to `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.jatcwang" % "sbt-difflicious" % "@VERSION@")
```

Then add the ZIO Test integration to `build.sbt`:

```scala
"com.github.jatcwang" %% "difflicious-zio-test" % "@VERSION@" % Test
```

and then mix `ZIODiffliciousSpec` into your specs and call `assertNoDiff` on any `Differ`.

```scala mdoc:nest
import difflicious.Differ
import difflicious.ziotest.ZIODiffliciousSpec
import zio.test.ZIOSpecDefault

object MySpec extends ZIOSpecDefault with ZIODiffliciousSpec {
  def spec = suite("MySpec")(
    test("a == b") {
      Differ[Int].assertNoDiff(1, 2)
    },
  )
}
```

`assertNoDiff` returns a `TestResult`, just like ZIO Test's own assertions such as `assertTrue`. You can combine it
with other assertions using `&&`, use it in a `for` comprehension, or use it inside `check`, where ZIO Test shrinks the
failing input as usual. As with any ZIO Test assertion, the result must be returned from the test: a result that is
neither returned nor combined with another is ignored. Enable the `-Wnonunit-statement` compiler option to have the
compiler warn about such a dropped result.

To check what an effect returns, map over it. To check its typed error, `flip` it first:

```scala
effect.map(differ.assertNoDiff(_, expected))
effect.flip.map(errorDiffer.assertNoDiff(_, expectedError))
```

On the JVM, `ZIODiffliciousSpec` writes reports for failed diffs, which you can explore using
**[Diff Viewer UI / CLI](../CLI.md)**. Each report records the labels of the suites enclosing the failed test.
JSONL report writing is not currently supported on Scala.js or Scala Native.

## ZIO data types

`Chunk` works like any other `Seq`. Specs that mix in `ZIODiffliciousSpec` also get a `Differ` for `NonEmptyChunk`.
Elsewhere, such as in an object of shared differs, import it:

```scala mdoc:nest
import difflicious.ziotest.implicits.*
import zio.NonEmptyChunk

val differ: Differ[NonEmptyChunk[Int]] = Differ[NonEmptyChunk[Int]]
```

## Overriding `aspects`

Reports are written by a test aspect that `ZIODiffliciousSpec` adds to `aspects`. If a spec loses that aspect, its tests
still fail and print their diffs, but no reports are written.

If a base trait overrides `aspects`, mix `ZIODiffliciousSpec` in after it. A base trait with a narrower `aspects` type
won't compile with the mixin. Examples are `Chunk[TestAspectPoly]`, `Chunk[TestAspectAtLeastR[Live]]` and, on Scala 2.13,
leaving the type to inference. Declare it with the type ZIO Test uses instead:
`Chunk[TestAspectAtLeastR[Environment with TestEnvironment]]`.

If a spec overrides `aspects` itself, either build on `super.aspects`, or replace it and include `diffliciousReporting`.
Don't do both, as each failed diff would then be reported twice.

Aspects that retry tests, such as `flaky`, must come before the reporting aspect in the chunk (for example
`TestAspect.flaky(3) +: super.aspects`). Otherwise a report is written for each failed attempt, even if a later attempt
passes.

```scala mdoc:nest
import zio.{Chunk, durationInt}
import zio.test.TestAspect

object MySlowSpec extends ZIOSpecDefault with ZIODiffliciousSpec {
  override def aspects = Chunk(TestAspect.timeout(120.seconds), diffliciousReporting)

  def spec = suite("MySlowSpec")(
    test("a == b") {
      Differ[Int].assertNoDiff(1, 1)
    },
  )
}
```

`diffliciousReporting` does nothing on Scala.js or Scala Native, where no reports are written.

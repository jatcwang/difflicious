---
id: munit
title: MUnit
---

# MUnit

Add the Difflicious sbt plugin to `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.jatcwang" % "sbt-difflicious" % "@VERSION@")
```

Then add the MUnit integration to `build.sbt`:

```scala
"com.github.jatcwang" %% "difflicious-munit" % "@VERSION@" % Test
```

and then extend `MUnitDiffliciousSuite` in your test suites and call `assertNoDiff` on any `Differ`.

```scala mdoc:nest
import munit.FunSuite
import difflicious.munit.MUnitDiffliciousSuite
import difflicious.Differ

class MyTest extends FunSuite with MUnitDiffliciousSuite {
  test("a == b") {
    Differ[Int].assertNoDiff(1, 2)
  }
}
```

`MUnitDiffliciousSuite` writes diff reports for failed diffs, which you can explore them using **[Diff Viewer UI / CLI](../CLI.md)**.

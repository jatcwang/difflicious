---
id: weaver
title: Weaver
---

# Weaver

Add the Difflicious sbt plugin to `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.jatcwang" % "sbt-difflicious" % "@VERSION@")
```

Then add the Weaver integration to `build.sbt`:

```scala
"com.github.jatcwang" %% "difflicious-weaver" % "@VERSION@" % Test
```

and then extend `WeaverDiffliciousSuite` in your test suites and call `assertNoDiff` on any `Differ`.

```scala mdoc:nest
import cats.effect.IO
import weaver.SimpleIOSuite
import difflicious.weaver.WeaverDiffliciousSuite
import difflicious.Differ

object MyTest extends SimpleIOSuite with WeaverDiffliciousSuite[IO] {
  pureTest("a == b") {
    Differ[Int].assertNoDiff(1, 2)
  }
}
```

`WeaverDiffliciousSuite` writes diff reports for failed diffs, which you can explore using **[Diff Viewer UI / CLI](../CLI.md)**.

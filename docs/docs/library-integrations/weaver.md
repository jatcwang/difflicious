---
id: weaver
title: Weaver
---

# Weaver

Add this to your SBT build
```
"com.github.jatcwang" %% "difflicious-weaver" % "@VERSION@" % Test
```

and then in your test suites you can call `assertNoDiff` on any `Differ`.

```scala mdoc:nest
import weaver.SimpleIOSuite
import difflicious.weaver.WeaverDiff.*
import difflicious.Differ

object MyTest extends SimpleIOSuite {
  pureTest("a == b") { 
    Differ[Int].assertNoDiff(1, 2)
  }
}
```

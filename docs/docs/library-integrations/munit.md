---
id: munit
title: MUnit
---

# MUnit

Add this to your SBT build
```
"com.github.jatcwang" %% "difflicious-munit" % "@VERSION@" % Test
```

and then in your test suites you can call `assertNoDiff` on any `Differ`.

```scala mdoc:nest
import munit.FunSuite
import difflicious.munit.MUnitDiff.*
import difflicious.Differ

class MyTest extends FunSuite {
  test("a == b") { 
    Differ[Int].assertNoDiff(1, 2)
  }
}
```

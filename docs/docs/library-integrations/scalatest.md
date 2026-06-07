---
id: scalatest
title: ScalaTest
---

# ScalaTest

Add this to your SBT build
```
"com.github.jatcwang" %% "difflicious-scalatest" % "@VERSION@" % Test
```

Tests should be run with the `-oW` option to disable ScalaTest from coloring test failures all red as it interferes with 
difflicious color display.

```
testOnly -- -oW
```

Here's an example of what a test using difflicious looks like:

```scala mdoc:nest
import org.scalatest.funsuite.AnyFunSuite
import difflicious.scalatest.ScalatestDiff.*
import difflicious.Differ

class MyTest extends AnyFunSuite {
  test("a == b") { 
    Differ[Int].assertNoDiff(1, 2)
  }
}
```

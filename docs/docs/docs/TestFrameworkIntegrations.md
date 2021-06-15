---
layout: docs
title:  "Test Framework Integrations"
permalink: docs/test-framework-integrations
---

# Test Framework Integrations

## MUnit

Add this to your SBT build
```
"com.github.jatcwang" %% "difflicious-munit" % "{{ site.version }}" % Test
```

and then in your test suites you can call `assertNoDiff` on any `Differ`.

```scala mdoc:nest
import munit.FunSuite
import difflicious.munit.MUnitDiff._
import difflicious.Differ

class MyTest extends FunSuite {
  test("a == b") { 
    Differ[Int].assertNoDiff(1, 2)
  }
}
```

## Scalatest

Add this to your SBT build
```
"com.github.jatcwang" %% "difflicious-scalatest" % "{{ site.version }}" % Test
```

and then in your test suites you can call `assertNoDiff` on any `Differ`.

```scala mdoc:nest
import org.scalatest.funsuite.AnyFunSuite
import difflicious.scalatest.ScalatestDiff._
import difflicious.Differ

class MyTest extends AnyFunSuite {
  test("a == b") { 
    Differ[Int].assertNoDiff(1, 2)
  }
}
```

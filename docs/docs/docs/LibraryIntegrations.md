---
layout: docs
title:  "Library Integrations"
permalink: docs/library-integrations
---

# Library Integrations

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

## Cats

Differ instances for cats data structures like `NonEmptyList` and `Chain` can be found in

```
"com.github.jatcwang" %% "difflicious-scalatest" % "{{ site.version }}" % Test
```

```scala mdoc:nest
import difflicious.Differ
import difflicious.cats.implicits._
import cats.data.{NonEmptyMap, NonEmptyList}

val differ: Differ[List[NonEmptyMap[String, NonEmptyList[Int]]]] = Differ[List[NonEmptyMap[String, NonEmptyList[Int]]]]
```




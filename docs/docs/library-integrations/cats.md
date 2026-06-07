---
id: cats
title: Cats
---

# Cats

Differ instances for cats data structures like `NonEmptyList` and `Chain` can be found in

```
"com.github.jatcwang" %% "difflicious-cats" % "@VERSION@" % Test
```

```scala mdoc:nest
import difflicious.Differ
import difflicious.cats.implicits.*
import cats.data.{NonEmptyMap, NonEmptyList}

val differ: Differ[List[NonEmptyMap[String, NonEmptyList[Int]]]] = Differ[List[NonEmptyMap[String, NonEmptyList[Int]]]]
```

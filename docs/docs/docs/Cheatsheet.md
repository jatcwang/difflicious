---
layout: docs
title:  "Cheatsheet"
permalink: docs/cheatsheet
---

### Basic imports

```scala mdoc:invisible
import difflicious.Example._
```

```scala mdoc:silent
import difflicious._
import difflicious.implicits._
```

### Deriving instances for case class and sealed traits (Scala 3 enums)

```scala mdoc:nest:silent
val differ = Differ.derive[Person]
```

### Configuring Differs

```scala mdoc:compile-only
val differ = Differ[Map[String, List[Person]]]

differ.configure(_.each)(_.pairBy(_.name))
differ.configure(_.each)(_.pairByIndex)

differ.ignoreAt(_.each.each.name)
// Equivalent to differ.configure(_.each.each.name)(_.ignore)

val anotherPersonListDiffer: Differ[List[Person]] = ???
differ.replace(_.each)(anotherPersonListDiffer)
```



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

### Summoning Differ instances

```scala mdoc:silent
val intListDiffer = Differ[List[Int]]
```

### Deriving instances for case class and sealed traits (Scala 3 enums)

```scala mdoc:nest:silent
val differ = Differ.derived[Person]
```

For classes with generic fields, you need to also ask for Differ instance of the field type (not just the generic type).

```scala mdoc:silent:nest
case class Box[A](
  content: List[A]
)

case class Factory[A](
  boxes: List[Box[A]]
)

implicit def boxDiffer[A](implicit listDiffer: Differ[List[A]]): Differ[Box[A]] = Differ.derived[Box[A]]
implicit def factoryDiffer[A](implicit boxesDiffer: Differ[List[Box[A]]]): Differ[Factory[A]]  = Differ.derived[Factory[A]]

val differ = Differ[Factory[Int]]
```

### Configuring Differs

```scala mdoc:compile-only
val differ = Differ[Map[String, List[Person]]]

differ.configure(_.each)(_.pairBy(_.name))
differ.configure(_.each)(_.pairByIndex)

differ.ignoreAt(_.each.each.name)
// Equivalent to differ.configure(_.each.each.name)(_.ignore)

// Replacing a differ at path
val anotherPersonListDiffer: Differ[List[Person]] = ???
differ.replace(_.each)(anotherPersonListDiffer)
```



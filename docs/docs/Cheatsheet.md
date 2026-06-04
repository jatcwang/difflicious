---
id: cheatsheet
title: Cheatsheet
---

### Basic imports

```scala mdoc:invisible
import difflicious.Example.*
```

```scala mdoc:silent
import difflicious.*
import difflicious.implicits.*
```

### Summoning Differ instances

```scala mdoc:silent
val intListDiffer = Differ[List[Int]]
```

### Deriving instances for case class and sealed traits (Scala 3 enums)

```scala mdoc:nest:silent
val differ = Differ.derived[Person]
```

Use `Differ.derivedDeep` when you want Difflicious to recursively derive missing field instances at that call site.
Existing instances in scope still take priority.

```scala mdoc:nest:silent
case class Address(city: String)
case class Employee(name: String, address: Address)

val differ = Differ.derivedDeep[Employee]
```

For classes with generic fields, `Differ.derived` needs you to also ask for the `Differ` instance of the field type
(not just the generic type).

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

If you do not need to name those intermediate instances, `derivedDeep` can derive the missing instances recursively:

```scala mdoc:silent:nest
val differ = Differ.derivedDeep[Factory[Int]]
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

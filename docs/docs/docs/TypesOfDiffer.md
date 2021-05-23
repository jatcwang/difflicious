---
layout: docs
title:  "Types of Differ"
permalink: docs/typesofdiffer
---

# Types of Differs

There are many types of basic Differs, each producing different kind of results.

// FIXME: Document how to ignore/update for all differs
// FIXME: value differ

# Differs for sealed traits and case classes (i.e. Algebraic Data Types)

You can derive `Differ` for a case class provided that there is a `Differ` instance for all your fields.

Similarly, you can derive a `Differ` for a sealed trait (Also called "Enum" in Scala 3) provided that we're able to 
derive a Differ for subclass of the sealed trait (or a Differ instance is already in scope for that subclass)

// FIXME delme
```scala mdoc:invisible
import difflicious.Example.printHtml
import difflicious.Differ
```

**Case class:**

```scala mdoc:silent
final case class Person(name: String, age: Int)

object Person {
  implicit val differ: Differ[Person] = Differ.derive[Person]
}
```

```scala mdoc:silent
Person.differ.diff(
  Person("Alice", 40),
  Person("Alice", 35)
)
```

<pre class="diff-render">
Person(
  name: "Alice",
  age: <span style="color: red;">40</span> -> <span style="color: green;">35</span>,
)
</pre>

**Sealed trait:**

```scala mdoc:silent
// Deriving Differ instance for sealed trait
sealed trait HousePet
final case class Dog(name: String, age: Int) extends HousePet
final case class Cat(name: String, livesLeft: Int) extends HousePet

object HousePet {
  implicit val differ: Differ[HousePet] = Differ.derive[HousePet]
}
```

```scala mdoc:silent
HousePet.differ.diff(
  Dog("Lucky", 1),
  Cat("Lucky", 1)
)
```

<pre class="diff-render">
<span style="color: red;">Dog</span> != <span style="color: green;">Cat</span>
<span style="color: red;">=== Obtained ===
Dog(
  name: "Lucky",
  age: 1,
)</span>
<span style="color: green;">=== Expected ===
Cat(
  name: "Lucky",
  livesLeft: 1,
)</span>
</pre>

# Seq Differ

Differ for sequences (`Differ.seqDiffer`) allow diffing immutable sequences like `Seq`, `List`, and `Vector`.

By default, Seq Differs will match elements by their index in the sequence.

In the example below

- age of Bob is wrong
- An unexpected Alice is in the list
- Charles is expected but missing

```scala mdoc:silent
val alice = Person("Alice", 30)
val bob = Person("Bob", 25)
val bob50 = Person("Bob", 50)
val charles = Person("Charles", 80)

Differ.seqDiffer[List, Person].diff(
  List(alice, bob50),
  List(alice, bob, charles)
)
```

<pre class="diff-render">
List(
  Person(
    name: "Alice",
    age: 30,
  ),
  Person(
    name: "Bob",
    age: <span style="color: red;">50</span> -> <span style="color: green;">25</span>,
  ),
  <span style="color: green;">Person(
    name: "Charles",
    age: 80,
  )</span>,
)
</pre>

## Match by field

In many test scenarios we actually don't care about order of things, as long as the two sequences 
contains the same elements. One example of this is inserting multiple records into a database and then retrieving them
, where you expect the same records to be returned by not necessarily in the original order.

In this case, you can configure a `SeqDiffer` to match pairs by a field instead.

```scala mdoc:silent
import difflicious.UpdatePath
import difflicious.DifferOp.MatchBy
import difflicious.Differ.SeqDiffer
```

```scala mdoc:silent
val defaultDiffer: SeqDiffer[List, Person] = Differ.seqDiffer[List, Person]
val differByName = defaultDiffer.matchBy(_.name)

differByName.diff(
  List(bob50, charles, alice),
  List(alice, bob, charles)
)
```

When we match by a person's name instead of index, we can now easily spot that Bob has the wrong age.

<pre class="diff-render">
List(
  Person(
    name: "Bob",
    age: <span style="color: red;">50</span> -> <span style="color: green;">25</span>,
  ),
  Person(
    name: "Charles",
    age: 80,
  ),
  Person(
    name: "Alice",
    age: 30,
  ),
)
</pre>

In some cases you only have a `Differ[List[A]]` which doesn't have the `matchBy` method.
In that case, you can still use `updateWith` to modify the differ.

```scala mdoc:silent
val differByName2 = defaultDiffer.updateWith(UpdatePath.current, MatchBy.func((p: Person) => p.name)).right.get
```

# Map differ

Map differ match entries with the same keys and compare the values. It will also indicate which 
keys are missing from either side.

It requires 

- a `ValueDiffer` instance for the map key type (for display purposes)
- a `Differ` instance for the map value type

```scala mdoc:silent
Differ.mapDiffer[Map, String, Person].diff(
  Map(
    "a" -> alice,
    "b" -> bob
  ),
  Map(
    "b" -> bob50,
    "c" -> charles
  ),
)
```

<pre class="diff-render">
Map(
  <span style="color: red;">"a"</span> -> <span style="color: red;">Person(
      name: "Alice",
      age: 30,
    )</span>,
  "b" -> Person(
      name: "Bob",
      age: <span style="color: red;">25</span> -> <span style="color: green;">50</span>,
    ),
  <span style="color: green;">"c"</span> -> <span style="color: green;">Person(
      name: "Charles",
      age: 80,
    )</span>,
)
</pre>

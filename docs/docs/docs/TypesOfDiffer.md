---
layout: docs
title:  "Types of Differ"
permalink: docs/types-of-differs
---

# Types of Differs

Here we list the kinds of Differs and how you can use them.

The examples below assume the following imports:

```scala mdoc:silent
import difflicious._
import difflicious.implicits._
```

# Value Differs

For basic types like `Int`, `Double` and `String` we typically can compare them directly e.g. using `equals` method.

If you have a simple type where you don't need any advanced diffing, then you can use `Differ.useEquals` to make a 
Differ instance for it.

```scala mdoc:silent
case class MyInt(i: Int)

object MyInt {
  implicit val differ: Differ[MyInt] = Differ.useEquals[MyInt](valueToString = _.toString)
}
```

```scala mdoc:silent
MyInt.differ.diff(MyInt(1), MyInt(2))
```

<pre class="diff-render">
<span style="color: red;">MyInt(1)</span> -> <span style="color: green;">MyInt(2)</span>
</pre>

# Differs for Algebraic Data Types (enums, sealed traits and case classes)

You can derive `Differ` for a case class provided that there is a `Differ` instance for all your fields.

Similarly, you can derive a `Differ` for a sealed trait (Also called **Enums** in Scala 3) provided that we're able to 
derive a Differ for subclass of the sealed trait (or a Differ instance is already in scope for that subclass)


### Case class

```scala mdoc:silent
final case class Person(name: String, age: Int)

object Person {
  implicit val differ: Differ[Person] = Differ.derived[Person]
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

### Sealed trait / Scala 3 Enum

```scala mdoc:silent
// Deriving Differ instance for sealed trait
sealed trait HousePet
final case class Dog(name: String, age: Int) extends HousePet
final case class Cat(name: String, livesLeft: Int) extends HousePet

object HousePet {
  implicit val differ: Differ[HousePet] = Differ.derived[HousePet]
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

Differ for sequences allow diffing immutable sequences like `Seq`, `List`, and `Vector`.

By default, Seq Differs will match elements by their index in the sequence.

In the example below

- **Bob**'s age
- **Alice** isn't expected to be in list
- **Charles** is expected but missing

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

## Pair by field

In many test scenarios we actually don't care about order of elements, as long as the two sequences 
contains the same elements. One example of this is inserting multiple records into a database and then retrieving them
, where you expect the same records to be returned by not necessarily in the original order.

In this case, you can configure a `Differ` to pair by a field instead.

```scala mdoc:silent
val differByName = Differ[List[Person]].pairBy(_.name)

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

# Map differ

Map differ pair entries with the same keys and compare the values. Missing key-values will also be reported in the result.

It requires 

- a `ValueDiffer` instance for the map key type (for display purposes)
- a `Differ` instance for the map value type

```scala mdoc:silent
Differ[Map[String, Person]].diff(
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

# Set differ

Set differ can diff two Sets by pairing the set elements and diffing them. 
By default, the pairing is based on matching elements that are equal to each other (using `equals`). 

However, you most likely want to pair elements using a field on an element instead for better diffs reports 
(See next section).

## Pair by field

For the best error reporting, you want to configure `SetDiffer` to pair by a field.

```scala mdoc:nest:silent
val differByName: Differ[Set[Person]] = Differ[Set[Person]].pairBy(_.name)

differByName.diff(
  Set(bob50, charles, alice),
  Set(alice, bob, charles)
)
```

<pre class="diff-render">
Set(
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

# Always ignored Differ

Sometimes for certain types you can't really compare them (e.g. Something that's not a plain data structure).

In that case you can use `Differ.alwaysIgnore`

```scala mdoc:silent
class CantCompare()

val alwaysIgnoredDiffer: Differ[CantCompare] = Differ.alwaysIgnore[CantCompare]
```


---
id: basic-differs
title: Basic Differs
---

# Basic Differs

This page covers basic `Differ` instances, including value Differs, collection Differs, and Differs for types that
should always be ignored.

The examples below assume the following imports:

```scala mdoc:silent
import difflicious.*
import difflicious.implicits.*
```

# Value Differs / `Differ.useEquals`

For basic types like `Int`, `Double` and `String` we typically can compare them directly, for example by using the
`equals` method.

If you have a type where you don't want any advanced diffing, then you can use `Differ.useEquals` to make a
`Differ` instance for it.

```scala mdoc:silent
case class MyInt(i: Int)

object MyInt {
  implicit val differ: Differ[MyInt] = Differ.useEquals[MyInt](valueToString = _.toString)
}
```

```scala mdoc:silent
MyInt.differ.diff(MyInt(1), MyInt(2))
```

<pre className="diff-render">
<span className="diff-red">MyInt(1)</span> -> <span className="diff-green">MyInt(2)</span>
</pre>

# Collection Differs

Difflicious provides `Differ` instances for common collection shapes such as sequences, maps, and sets.

```scala mdoc:silent
case class Person(name: String, age: Int)

object Person {
  implicit val differ: Differ[Person] = Differ.derived[Person]
}
```

# Seq Differ

Differs for sequences allow diffing immutable sequences like `Seq`, `List`, and `Vector`.

By default, Seq Differs will match elements by their index in the sequence.

In the example below:

- Bob's age is different
- Alice isn't expected to be in the list
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

<pre className="diff-render">
List(
  Person(
    name: "Alice",
    age: 30,
  ),
  Person(
    name: "Bob",
    age: <span className="diff-red">50</span> -> <span className="diff-green">25</span>,
  ),
  <span className="diff-green">Person(
    name: "Charles",
    age: 80,
  )</span>,
)
</pre>

## Pair by field

In many test scenarios we actually don't care about order of elements, as long as the two sequences contain the same
elements. One example of this is inserting multiple records into a database and then retrieving them, where you expect
the same records to be returned but not necessarily in the original order.

In this case, you can configure a `Differ` to pair by a field instead.

```scala mdoc:silent
val differByName = Differ[List[Person]].pairBy(_.name)

differByName.diff(
  List(bob50, charles, alice),
  List(alice, bob, charles)
)
```

When we match by a person's name instead of index, we can now easily spot that Bob has the wrong age.

<pre className="diff-render">
List(
  Person(
    name: "Bob",
    age: <span className="diff-red">50</span> -> <span className="diff-green">25</span>,
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

# Map Differ

Map Differs pair entries with the same keys and compare the values. Missing key-values will also be reported in the
result.

It requires:

- a `ValueDiffer` instance for the map key type, for display purposes
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

<pre className="diff-render">
Map(
  <span className="diff-red">"a"</span> -> <span className="diff-red">Person(
      name: "Alice",
      age: 30,
    )</span>,
  "b" -> Person(
      name: "Bob",
      age: <span className="diff-red">25</span> -> <span className="diff-green">50</span>,
    ),
  <span className="diff-green">"c"</span> -> <span className="diff-green">Person(
      name: "Charles",
      age: 80,
    )</span>,
)
</pre>

# Set Differ

Set Differs can diff two Sets by pairing the set elements and diffing them.
By default, the pairing is based on matching elements that are equal to each other using `equals`.

However, you most likely want to pair elements using a field on an element instead for better diff reports.

## Pair by field

For the best error reporting, you want to configure `SetDiffer` to pair by a field.

```scala mdoc:nest:silent
val differByName: Differ[Set[Person]] = Differ[Set[Person]].pairBy(_.name)

differByName.diff(
  Set(bob50, charles, alice),
  Set(alice, bob, charles)
)
```

<pre className="diff-render">
Set(
  Person(
    name: "Bob",
    age: <span className="diff-red">50</span> -> <span className="diff-green">25</span>,
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

# Always Ignored Differ

Sometimes for certain types you can't really compare them, for example when the type is not a plain data structure.

In that case you can use `Differ.alwaysIgnore`.

```scala mdoc:silent
class CantCompare()

val alwaysIgnoredDiffer: Differ[CantCompare] = Differ.alwaysIgnore[CantCompare]
```

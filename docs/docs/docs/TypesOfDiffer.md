---
layout: docs
title:  "Types of Differ"
permalink: docs/types-of-differs
---

# Types of Differs

Difflicious provides various types of differs to handle different kinds of data structures. This page explains each type of differ and how to use them effectively.

The examples below assume the following imports:

```scala mdoc:silent
import difflicious._
import difflicious.implicits._
```

## Value Differs

Value differs are used for basic types like `Int`, `Double`, and `String` that can be compared directly using their `equals` method.

### Numeric Differs

For numeric types (`Int`, `Long`, `Double`, etc.), Difflicious uses a `NumericDiffer` that compares values using the appropriate numeric equality.

```scala mdoc:silent
val intDiffer: Differ[Int] = Differ[Int]
val doubleDiffer: Differ[Double] = Differ[Double]

// Example usage
val intResult = intDiffer.diff(5, 10)
val doubleResult = doubleDiffer.diff(5.5, 5.5)
```

Output for the integer comparison:

<pre class="diff-render">
<span style="color: red;">5</span> -> <span style="color: green;">10</span>
</pre>

### String Differ

Strings are compared character by character:

```scala mdoc:silent
val stringDiffer: Differ[String] = Differ[String]

// Example usage
val stringResult = stringDiffer.diff("hello", "world")
```

Output:

<pre class="diff-render">
<span style="color: red;">"hello"</span> -> <span style="color: green;">"world"</span>
</pre>

### Custom Value Differs

You can create custom value differs for your own types using `Differ.useEquals`:

```scala mdoc:silent
case class UserId(value: String)

val userIdDiffer: Differ[UserId] = Differ.useEquals[UserId](_.toString)
```

## Collection Differs

Difflicious provides specialized differs for collections like lists, sets, and maps.

### Sequence Differs (List, Vector, etc.)

By default, sequences are compared by matching elements at the same index:

```scala mdoc:silent
val listDiffer: Differ[List[Int]] = Differ[List[Int]]

// Example usage
val listResult = listDiffer.diff(List(1, 2, 3), List(1, 4, 3))
```

Output:

<pre class="diff-render">
List(
  1,
  <span style="color: red;">2</span> -> <span style="color: green;">4</span>,
  3
)
</pre>

You can customize how elements are paired using `.pairBy` or `.pairByIndex`:

```scala mdoc:silent
case class Person(name: String, age: Int)
implicit val personDiffer: Differ[Person] = Differ.derived[Person]

// Pair by name instead of index
val personListDiffer = Differ[List[Person]].pairBy(_.name)

// Example usage
val personListResult = personListDiffer.diff(
  List(Person("Alice", 30), Person("Bob", 25)),
  List(Person("Bob", 40), Person("Alice", 35))
)
```

Output:

<pre class="diff-render">
List(
  Person(
    name: "Alice",
    age: <span style="color: red;">30</span> -> <span style="color: green;">35</span>
  ),
  Person(
    name: "Bob",
    age: <span style="color: red;">25</span> -> <span style="color: green;">40</span>
  )
)
</pre>

### Set Differs

Sets are compared by matching elements with the same value:

```scala mdoc:silent
val setDiffer: Differ[Set[Int]] = Differ[Set[Int]]

// Example usage
val setResult = setDiffer.diff(Set(1, 2, 3), Set(1, 3, 4))
```

Output:

<pre class="diff-render">
Set(
  1,
  3,
  <span style="color: red;">2</span>,
  <span style="color: green;">4</span>
)
</pre>

### Map Differs

Maps are compared by matching entries with the same key and then comparing their values:

```scala mdoc:silent
val mapDiffer: Differ[Map[String, Int]] = Differ[Map[String, Int]]

// Example usage
val mapResult = mapDiffer.diff(
  Map("a" -> 1, "b" -> 2, "c" -> 3),
  Map("a" -> 1, "b" -> 5, "d" -> 4)
)
```

Output:

<pre class="diff-render">
Map(
  "a" -> 1,
  "b" -> <span style="color: red;">2</span> -> <span style="color: green;">5</span>,
  <span style="color: red;">"c" -> 3</span>,
  <span style="color: green;">"d" -> 4</span>
)
</pre>

## Product Differs (Case Classes)

For case classes and other product types, Difflicious derives a differ that compares each field:

```scala mdoc:silent
case class Address(street: String, city: String, zipCode: String)
case class User(name: String, age: Int, address: Address)

implicit val addressDiffer: Differ[Address] = Differ.derived[Address]
implicit val userDiffer: Differ[User] = Differ.derived[User]

// Example usage
val userResult = userDiffer.diff(
  User("Alice", 30, Address("123 Main St", "New York", "10001")),
  User("Alice", 35, Address("123 Main St", "Boston", "02108"))
)
```

Output:

<pre class="diff-render">
User(
  name: "Alice",
  age: <span style="color: red;">30</span> -> <span style="color: green;">35</span>,
  address: Address(
    street: "123 Main St",
    city: <span style="color: red;">"New York"</span> -> <span style="color: green;">"Boston"</span>,
    zipCode: <span style="color: red;">"10001"</span> -> <span style="color: green;">"02108"</span>
  )
)
</pre>

## Sum Type Differs (Sealed Traits/Enums)

For sealed traits, enums, and other sum types, Difflicious compares the concrete type first, then the fields:

```scala mdoc:silent
sealed trait Shape
object Shape {
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  
  implicit val differ: Differ[Shape] = Differ.derived[Shape]
}

import Shape._

// Example usage
val shapeResult = Shape.differ.diff(
  Circle(5.0),
  Rectangle(10.0, 5.0)
)
```

Output:

<pre class="diff-render">
<span style="color: red;">Circle</span> != <span style="color: green;">Rectangle</span>
<span style="color: red;">=== Obtained ===
Circle(
  radius: 5.0
)</span>
<span style="color: green;">=== Expected ===
Rectangle(
  width: 10.0,
  height: 5.0
)</span>
</pre>

If the concrete types match, it will compare their fields:

```scala mdoc:silent
val circleResult = Shape.differ.diff(
  Circle(5.0),
  Circle(7.5)
)
```

Output:

<pre class="diff-render">
Circle(
  radius: <span style="color: red;">5.0</span> -> <span style="color: green;">7.5</span>
)
</pre>

## Nested Collection Differs

Difflicious handles nested collections gracefully:

```scala mdoc:silent
val nestedDiffer: Differ[Map[String, List[Person]]] = Differ[Map[String, List[Person]]]

// Example usage
val nestedResult = nestedDiffer.diff(
  Map(
    "team1" -> List(Person("Alice", 30), Person("Bob", 25)),
    "team2" -> List(Person("Charlie", 40))
  ),
  Map(
    "team1" -> List(Person("Alice", 35), Person("Bob", 25)),
    "team3" -> List(Person("Dave", 50))
  )
)
```

Output:

<pre class="diff-render">
Map(
  "team1" -> List(
    Person(
      name: "Alice",
      age: <span style="color: red;">30</span> -> <span style="color: green;">35</span>
    ),
    Person(
      name: "Bob",
      age: 25
    )
  ),
  <span style="color: red;">"team2" -> List(
    Person(
      name: "Charlie",
      age: 40
    )
  )</span>,
  <span style="color: green;">"team3" -> List(
    Person(
      name: "Dave",
      age: 50
    )
  )</span>
)
</pre>

## Always Ignored Differ

Sometimes for certain types you can't really compare them (e.g., something that's not a plain data structure).

In that case you can use `Differ.alwaysIgnore`:

```scala mdoc:silent
class CantCompare()

val alwaysIgnoredDiffer: Differ[CantCompare] = Differ.alwaysIgnore[CantCompare]
```

When using this differ, the comparison will always be treated as successful, regardless of the actual values.

## Java Time Differs

Difflicious provides built-in differs for Java Time types like `Instant`, `LocalDate`, and `ZonedDateTime`:

```scala mdoc:silent
import java.time.{Instant, LocalDate}

val instantDiffer: Differ[Instant] = Differ[Instant]
val localDateDiffer: Differ[LocalDate] = Differ[LocalDate]

// Example usage
val instantResult = instantDiffer.diff(
  Instant.parse("2023-01-01T00:00:00Z"),
  Instant.parse("2023-01-02T00:00:00Z")
)
```

Output:

<pre class="diff-render">
<span style="color: red;">"2023-01-01T00:00:00Z"</span> -> <span style="color: green;">"2023-01-02T00:00:00Z"</span>
</pre>

## Integration with Other Libraries

Difflicious provides integrations with popular libraries like Cats. For example, with the `difflicious-cats` module, you can diff Cats data structures like `NonEmptyList` and `Chain`:

```scala
import difflicious.cats.implicits._
import cats.data.NonEmptyList

val nelDiffer: Differ[NonEmptyList[Int]] = Differ[NonEmptyList[Int]]
```

## Summary

Difflicious provides a rich set of differs for various types of data structures. By understanding the different types of differs and how they work, you can effectively use Difflicious to compare complex data structures in your tests.

For more information on how to customize these differs, see the [Configuring Differs](docs/configuring-differs) page.


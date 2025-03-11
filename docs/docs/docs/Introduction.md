---
layout: docs
title:  "Introduction"
permalink: docs/introduction
---

# Introduction to Difflicious

**Difflicious** is a library that produces readable and actionable diffs in your tests, making it easier to understand why tests are failing and what needs to be fixed.

## Key Features

* **Human-Readable Diff Output**: Color-coded, structured output that makes differences immediately apparent
* **Highly Customizable**: Configure exactly how you want to compare values
  * Ignore non-deterministic or irrelevant fields
  * Compare collections independent of order
  * Match elements by key rather than position
* **Type-Safe API**: Fully leverages Scala's type system for compile-time safety
* **Test Framework Integrations**: Works with MUnit, ScalaTest, and Weaver
* **Performance Optimized**: Efficient comparison algorithms even for large data structures
* **Cross-Platform**: Works with both Scala 2.13 and Scala 3

## Core Concepts

Difflicious is built around three key abstractions:

### 1. The `Differ` Type Class

The `Differ` type class knows how to compare values of a specific type and produce a diff result:

```scala
trait Differ[T] {
  type R <: DiffResult
  def diff(obtained: T, expected: T): R
  // Configuration methods...
}
```

Differs can be derived automatically for case classes and sealed traits, or created manually for custom types.

### 2. The `DiffResult` Output

A `DiffResult` contains structured information about the differences between values:

```scala
sealed trait DiffResult {
  def isOk: Boolean           // Whether the comparison was successful
  def isIgnored: Boolean      // Whether this differ was marked as ignored
  def pairType: PairType      // Whether both sides, or just one side, was present
}
```

There are specialized `DiffResult` subtypes for different kinds of data, like lists, records, and primitive values.

### 3. Configuration Operations

Differs can be configured using a path-based syntax:

```scala
// Ignore a specific field
personDiffer.ignoreAt(_.age)

// Configure how collections are matched
listDiffer.pairBy(_.id)

// Configure a nested differ
rootDiffer.configure(_.items.each)(_.ignoreAt(_.timestamp))
```

## How It Works

When a test using Difflicious runs:

1. The `diff` method compares the actual and expected values
2. If differences are found, a structured `DiffResult` is produced
3. The `DiffResult` is formatted into a human-readable output
4. The test assertion fails with the formatted output

The result is a clear, actionable message that helps you understand exactly what's wrong.

## Use Cases

Difflicious excels in many testing scenarios:

* **API Testing**: Compare complex JSON responses with expected values
* **Data Processing**: Verify transformations produce the expected output
* **Integration Testing**: Compare database query results
* **Snapshot Testing**: Compare current outputs against golden masters

## Example: Comparing Complex Objects

Let's see Difflicious in action with a practical example:

```scala mdoc:silent
import difflicious._
import difflicious.implicits._

// Define a domain model
case class Address(street: String, city: String, zipCode: String)
case class User(id: String, name: String, email: String, address: Address)

// Derive differs
implicit val addressDiffer: Differ[Address] = Differ.derived[Address]
implicit val userDiffer: Differ[User] = Differ.derived[User]

// Configure for a specific test
val flexibleDiffer = userDiffer
  .ignoreAt(_.email)        // Ignore email differences
  .ignoreAt(_.address.zipCode)  // Ignore zip code differences

// Compare two users
val actual = User("123", "Alice Smith", "alice@example.com", 
                 Address("123 Main St", "New York", "10001"))
val expected = User("123", "Alice Johnson", "alice.new@example.com", 
                   Address("123 Main St", "Boston", "02108"))

// Generate a diff
val result = flexibleDiffer.diff(actual, expected)
```

This would produce a diff like:

<pre class="diff-render">
User(
  id: "123",
  name: <span style="color: red;">"Alice Smith"</span> -> <span style="color: green;">"Alice Johnson"</span>,
  email: <span style="color: gray;">[IGNORED]</span>,
  address: Address(
    street: "123 Main St",
    city: <span style="color: red;">"New York"</span> -> <span style="color: green;">"Boston"</span>,
    zipCode: <span style="color: gray;">[IGNORED]</span>
  )
)
</pre>

## Motivational Example

Here's a simple example showing Difflicious in action:

```scala mdoc:silent
import difflicious._
import difflicious.implicits._

sealed trait HousePet {
  def name: String
}
object HousePet {
  final case class Dog(name: String, age: Int) extends HousePet
  final case class Cat(name: String, livesLeft: Int) extends HousePet
  
  implicit val differ: Differ[HousePet] = Differ.derived
}

import HousePet.{Cat, Dog}

val petsDiffer = Differ[List[HousePet]]
  .pairBy(_.name)                          // Match pets in the list by name for comparison
  .ignoreAt(_.each.subType[Cat].livesLeft) // Don't worry about livesLeft for cats when comparing
  
val actualPets = List(
  Dog("Andy", 12),
  Cat("Dr.Evil", 7),
  Dog("Lucky", 5)
)

val expectedPets = List(
  Cat("Andy", 9),
  Cat("Dr.Evil", 8),
  Dog("Lucky", 6)
)

// This would be used in a test assertion
petsDiffer.diff(actualPets, expectedPets)
```

And this is the diff you would see:

<pre class="diff-render">
List(
  <span style="color: red;">Dog</span> != <span style="color: green;">Cat</span>
  <span style="color: red;">=== Obtained ===
  Dog(
    name: "Andy",
    age: 12,
  )</span>
  <span style="color: green;">=== Expected ===
  Cat(
    name: "Andy",
    livesLeft: [IGNORED],
  )</span>,
  Cat(
    name: "Dr.Evil",
    livesLeft: <span style="color: gray;">[IGNORED]</span>,
  ),
  Dog(
    name: "Lucky",
    age: <span style="color: red;">5</span> -> <span style="color: green;">6</span>,
  ),
)
</pre>

In this example, you can see that:

* Difflicious spots that **Andy** is a Dog in the actual result but a Cat in the expected result
* The cat **Dr.Evil** is considered to be the same on both sides, because we decided to ignore how many lives the cats have left
* A diff is produced showing us that **Lucky's** age is wrong (5 instead of 6)

## Why Use Difflicious?

Difflicious shines when you're testing complex data structures. Instead of just telling you that two values aren't equal, it shows you exactly what's different, making it much easier to fix the issue.

It's particularly useful for:

* Testing data transformations
* Validating API responses
* Comparing database query results
* Testing serialization/deserialization

## Advantages Over Standard Assertions

While most test frameworks have some form of equality assertion, Difflicious offers several advantages:

### 1. Structured Output vs. Flat String Comparison

Standard assertions often produce flat string comparisons:

```
Expected: User(id=123, name="Alice", email="alice@example.com")
Actual:   User(id=123, name="Alice", email="alice.new@example.com")
```

Difflicious provides a hierarchical view that makes it easy to spot differences:

<pre class="diff-render">
User(
  id: 123,
  name: "Alice",
  email: <span style="color: red;">"alice@example.com"</span> -> <span style="color: green;">"alice.new@example.com"</span>
)
</pre>

### 2. Field-By-Field Comparison

With standard assertions, you only know that objects are different, not which specific fields differ. Difflicious shows exactly which fields are different, making debugging much faster.

### 3. Customizable Matching

Difflicious lets you define what constitutes "equality" for your specific test needs:

```scala
// Ignore timestamps when comparing events
val eventDiffer = Differ[Event].ignoreAt(_.timestamp)

// Compare users by ID only
val userDiffer = Differ.useEquals[User](u => s"User(id=${u.id})")
```

### 4. Collection Matching Strategies

Standard assertions compare collections by position. Difflicious can match elements by key:

```scala
// Match users by ID regardless of order in the list
val userListDiffer = Differ[List[User]].pairBy(_.id)
```

This is especially useful for testing database queries or API responses where order may not be guaranteed.

## Real-World Example: API Testing

Here's a more practical example showing how Difflicious can be used for API testing:

```scala mdoc:silent
// Define a domain model
case class Address(street: String, city: String, zipCode: String)
case class User(id: String, name: String, email: String, address: Address, createdAt: Long)

// Derive differs
implicit val addressDiffer: Differ[Address] = Differ.derived[Address]
implicit val userDiffer: Differ[User] = Differ.derived[User]

// Configure for API testing - ignore non-deterministic fields
val apiTestDiffer = userDiffer
  .ignoreAt(_.createdAt)        // Ignore timestamps
  .ignoreAt(_.address.zipCode)  // Ignore zip code differences

// Compare API response with expected result
val apiResponse = User(
  "123", 
  "Alice Smith", 
  "alice@example.com", 
  Address("123 Main St", "New York", "10001"),
  System.currentTimeMillis()
)

val expectedUser = User(
  "123", 
  "Alice Smith", 
  "alice@example.com", 
  Address("123 Main St", "Boston", "02108"),
  0 // We don't care about this value
)

// Generate a diff
val result = apiTestDiffer.diff(apiResponse, expectedUser)
```

This would produce a diff like:

<pre class="diff-render">
User(
  id: "123",
  name: "Alice Smith",
  email: "alice@example.com",
  address: Address(
    street: "123 Main St",
    city: <span style="color: red;">"New York"</span> -> <span style="color: green;">"Boston"</span>,
    zipCode: <span style="color: gray;">[IGNORED]</span>
  ),
  createdAt: <span style="color: gray;">[IGNORED]</span>
)
</pre>

This makes it immediately clear that the only meaningful difference is the city field.

## Next Steps

Ready to get started? Check out the [Quickstart](docs/quickstart) guide to begin using Difflicious in your tests.

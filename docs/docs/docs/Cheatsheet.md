---
layout: docs
title:  "Cheatsheet"
permalink: docs/cheatsheet
---

# Difflicious Cheatsheet

This page provides a quick reference for common Difflicious operations.

## Basic Imports

```scala mdoc:invisible
import difflicious.Example._
```

```scala mdoc:silent
// Core functionality
import difflicious._

// Additional helpers and syntax
import difflicious.implicits._
```

## Obtaining Differ Instances

```scala mdoc:silent
// For primitive types
val intDiffer = Differ[Int]
val stringDiffer = Differ[String]

// For collections
val intListDiffer = Differ[List[Int]]
val stringMapDiffer = Differ[Map[String, String]]

// For Java time types
import java.time.Instant
val instantDiffer = Differ[Instant]
```

## Deriving Differs for Custom Types

### Case Classes

```scala mdoc:nest:silent
case class Person(name: String, age: Int)

// Derive a differ for a case class
val personDiffer = Differ.derived[Person]

// Or as an implicit
implicit val implicitPersonDiffer: Differ[Person] = Differ.derived[Person]
```

### Sealed Traits / Enums

```scala mdoc:nest:silent
sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

// Derive a differ for a sealed trait
implicit val shapeDiffer: Differ[Shape] = Differ.derived[Shape]
```

### Generic Types

For classes with generic fields, you need to also provide instances for the field types:

```scala mdoc:silent:nest
case class Box[A](
  content: List[A]
)

case class Factory[A](
  boxes: List[Box[A]]
)

// Need to create differs for each level
implicit def boxDiffer[A](implicit listDiffer: Differ[List[A]]): Differ[Box[A]] = 
  Differ.derived[Box[A]]
  
implicit def factoryDiffer[A](implicit boxesDiffer: Differ[List[Box[A]]]): Differ[Factory[A]] = 
  Differ.derived[Factory[A]]

val intFactoryDiffer = Differ[Factory[Int]]
```

## Using Differs

### Basic Comparison

```scala mdoc:silent:nest
// Compare two values
val result = personDiffer.diff(
  Person("Alice", 30),
  Person("Alice", 35)
)

// Check if the comparison was successful
val isOk = result.isOk
```

### Assertion in Tests

```scala mdoc:compile-only
import difflicious.munit.MUnitDiff._

// Assert that two values are the same (example with MUnit)
personDiffer.assertNoDiff(
  Person("Alice", 30),
  Person("Alice", 30)
)
```

## Ignoring Fields

```scala mdoc:silent:nest
// Ignore a single field
val ageIgnoredDiffer = personDiffer.ignoreAt(_.age)

// Ignore nested fields
case class Employee(person: Person, department: String)
implicit val employeeDiffer: Differ[Employee] = Differ.derived[Employee]

val ageIgnoredEmployeeDiffer = employeeDiffer.ignoreAt(_.person.age)
```

## Configuring Collection Differs

```scala mdoc:silent:nest
// Match list elements by a key function
val personListDiffer = Differ[List[Person]].pairBy(_.name)

// Explicitly match by index (default behavior)
val indexMatchedDiffer = Differ[List[Person]].pairByIndex

// Configure nested collection elements
val nestedListDiffer = Differ[List[List[Person]]].configure(_.each)(_.pairBy(_.name))
```

## Path Expression Syntax

```scala mdoc:compile-only
// Field access
differ.ignoreAt(_.fieldName)

// Nested field access
differ.ignoreAt(_.outerField.innerField)

// Collection elements
differ.ignoreAt(_.listField.each.someField)

// Specific subtypes in sealed traits
sealed trait Animal
case class Dog(name: String, breed: String) extends Animal
case class Cat(name: String, lives: Int) extends Animal

val animalDiffer = Differ[Animal]
val catLivesIgnoredDiffer = animalDiffer.ignoreAt(_.subType[Cat].lives)

// Complex nested paths
differ.ignoreAt(_.field1.listField.each.subType[SomeType].nestedField)
```

## Custom Configuration

```scala mdoc:compile-only
// Replace a differ at a specific path
val customDiffer = Differ.useEquals[Person](p => s"Person(${p.name})")
val customizedDiffer = Differ[List[Person]].replace(_.each)(customDiffer)

// Apply multiple configurations
val multiConfigDiffer = Differ[Map[String, List[Person]]]
  .configure(_.each)(_.pairBy(_.name))
  .ignoreAt(_.each.each.age)
  .replace(_.each.each)(customDiffer)
```

## Creating Custom Differs

### Using Equality

```scala mdoc:silent:nest
// Custom differ using equality and a toString function
case class UserId(value: String)
val userIdDiffer = Differ.useEquals[UserId](id => s"UserId(${id.value})")
```

### Using Contramap

```scala mdoc:silent:nest
// Transform a differ for one type to work with another
case class Email(address: String)
val emailDiffer = Differ.stringDiffer.contramap[Email](_.address)
```

### Always Ignore

```scala mdoc:silent:nest
// A differ that always ignores differences
class Connection(/* complex type */)
val connectionDiffer = Differ.alwaysIgnore[Connection]
```

## Auto Derivation (Scala 3)

```scala
// In Scala 3, you can use auto derivation
import difflicious._
import difflicious.generic.auto.given

// This will automatically derive a Differ for Person
val result = Differ[Person].diff(person1, person2)
```

## Pre-configured Differs

```scala mdoc:silent:nest
// Reusable pre-configured differs
val standardPersonDiffer = Differ.derived[Person]
val lenientPersonDiffer = standardPersonDiffer.ignoreAt(_.age)
val veryLenientPersonDiffer = lenientPersonDiffer.ignoreAt(_.name)
```

## Visual Examples of Diff Outputs

Here are examples of how Difflicious diffs appear for different scenarios:

### Simple Value Differences

<pre class="diff-render">
<span style="color: red;">42</span> -> <span style="color: green;">43</span>
</pre>

### String Differences

<pre class="diff-render">
<span style="color: red;">"hello world"</span> -> <span style="color: green;">"hello there"</span>
</pre>

### Case Class Differences

<pre class="diff-render">
Person(
  name: "Alice",
  age: <span style="color: red;">30</span> -> <span style="color: green;">35</span>,
  email: <span style="color: red;">"alice@example.com"</span> -> <span style="color: green;">"alice.smith@example.com"</span>
)
</pre>

### List Differences

<pre class="diff-render">
List(
  "apple",
  <span style="color: red;">"banana"</span> -> <span style="color: green;">"orange"</span>,
  "pear",
  <span style="color: green;">"grape"</span>
)
</pre>

### Lists With Paired Elements

<pre class="diff-render">
List(
  User(
    id: "123",
    name: <span style="color: red;">"Alice"</span> -> <span style="color: green;">"Alice Smith"</span>
  ),
  User(
    id: "456",
    name: "Bob"
  ),
  <span style="color: red;">User(
    id: "789",
    name: "Charlie"
  )</span>
)
</pre>

### Ignored Fields

<pre class="diff-render">
User(
  id: "123",
  name: <span style="color: red;">"Alice"</span> -> <span style="color: green;">"Alice Smith"</span>,
  email: <span style="color: gray;">[IGNORED]</span>,
  age: 30
)
</pre>

### Sum Type Differences

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

### Map Differences

<pre class="diff-render">
Map(
  "a" -> 1,
  "b" -> <span style="color: red;">2</span> -> <span style="color: green;">3</span>,
  <span style="color: red;">"c" -> 4</span>,
  <span style="color: green;">"d" -> 5</span>
)
</pre>

### Nested Structures

<pre class="diff-render">
Company(
  name: "Acme Corp",
  departments: List(
    Department(
      name: "Engineering",
      employees: List(
        Employee(
          name: "Alice",
          role: <span style="color: red;">"Developer"</span> -> <span style="color: green;">"Senior Developer"</span>,
          startDate: <span style="color: gray;">[IGNORED]</span>
        ),
        Employee(
          name: "Bob",
          role: "Designer",
          startDate: <span style="color: gray;">[IGNORED]</span>
        )
      )
    ),
    Department(
      name: "Marketing",
      employees: List(
        <span style="color: green;">Employee(
          name: "Charlie",
          role: "Manager",
          startDate: [IGNORED]
        )</span>
      )
    )
  )
)
</pre>

## Common Patterns Quick Reference

Here are some common patterns you might use in your tests:

### Ignoring Non-Deterministic Fields

```scala
// Ignore timestamps, UUIDs, or other non-deterministic values
val differ = Differ[Event]
  .ignoreAt(_.timestamp)
  .ignoreAt(_.uuid)
```

### Comparing Collections Regardless of Order

```scala
// Match elements by ID regardless of their position in the list
val differ = Differ[List[User]].pairBy(_.id)
```

### Focusing on Specific Fields

```scala
// Only compare the fields you care about
val differ = userDiffer
  .ignoreAt(_.email)
  .ignoreAt(_.address)
  .ignoreAt(_.phoneNumber)
```

### Handling Nested Collections

```scala
// Configure how to compare elements in nested collections
val differ = Differ[Department]
  .configure(_.employees)(_.pairBy(_.id))
  .ignoreAt(_.employees.each.hireDate)
```

### Testing with Different Versions

```scala
// When comparing different versions of an API response
val differ = Differ[ApiResponse]
  .ignoreAt(_.version)
  .ignoreAt(_.newFieldsAddedInV2)
```

## Best Practices

1. **Keep base differs simple**: Define basic derived differs in companion objects
2. **Create custom differs for specific tests**: Configure differs based on test needs
3. **Use descriptive names**: Name your differs according to what they ignore or how they match
4. **Break down complex configurations**: Build complex differs step by step
5. **Use the most specific configuration first**: Configure nested collections before configuring their elements

## Quick Debugging

If your differ configuration isn't working:

1. Check your path expressions carefully
2. Ensure all required differs are in scope
3. Add type annotations if the compiler is confused
4. Break down complex configurations into smaller steps
5. Print the intermediate DiffResult to see exactly what's being compared

```scala mdoc:silent:nest
// Debug a differ configuration
val result = personDiffer.diff(person1, person2)
println(result.isOk) // Check if the comparison succeeded
```



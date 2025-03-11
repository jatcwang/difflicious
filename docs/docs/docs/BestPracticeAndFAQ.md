---
layout: docs
title:  "Best Practices and Frequently Asked Questions"
permalink: docs/best-practices-and-faq
---

# Best Practices and Frequently Asked Questions

This page provides guidance on how to use Difflicious effectively and answers common questions.

## Best Practices

### Managing `Differ` Instances

Tests are often the last check for the correctness of your program before it gets deployed, so we need to be careful about how we manage our `Differ` instances.

Here are some tips to help you best manage `Differ` instances when using Difflicious for testing:

* **Keep base differs simple**: Only put unmodified derived Differ instances in the implicit scope
  * This avoids the scenario where a modified Differ is pulled in accidentally during derivation, which can result in passing tests that otherwise should fail.
* **Scope customized differs locally**: If you need a modified Differ instance to be used in a derivation, scope it locally to the test where it's needed.

```scala mdoc:silent
import difflicious._

// Good: Base differ in implicit scope
case class User(id: String, name: String, email: String, createdAt: Long)
object User {
  implicit val differ: Differ[User] = Differ.derived[User]
}

// Good: Test-specific differ scoped locally
class UserTest {
  // This differ ignores createdAt, but only for this specific test
  val userDifferIgnoringCreatedAt = User.differ.ignoreAt(_.createdAt)
  
  // Use userDifferIgnoringCreatedAt in your test...
}

// Bad: Modified differ in implicit scope
object BadPractice {
  case class BadUser(id: String, name: String, createdAt: Long)
  
  // This will affect ALL tests using BadUser
  implicit val differ: Differ[BadUser] = Differ.derived[BadUser].ignoreAt(_.createdAt)
}
```

### Organizing Differs for Large Projects

For larger projects with many custom types:

1. **Co-locate differs with their types**: Define the implicit `Differ` instance in the companion object of the type
2. **Create a dedicated `instances` package**: For third-party types you don't control
3. **Use package objects for common imports**: Create a package object with common imports to reduce boilerplate

```scala
// In your domain model
case class Customer(id: String, name: String)
object Customer {
  implicit val differ: Differ[Customer] = Differ.derived[Customer]
}

// For third-party types
package myapp.instances

import thirdparty.ExternalType
import difflicious._

object ExternalInstances {
  implicit val externalTypeDiffer: Differ[ExternalType] = 
    Differ.useEquals[ExternalType](_.toString)
}

// Package object for common imports
package object myapp {
  val commonImports = new {
    import difflicious._
    import difflicious.implicits._
    import myapp.instances.ExternalInstances._
  }
}

// In your tests
import myapp.commonImports._
```

## Performance Optimization

When dealing with large data structures, performance can become a concern. Here are strategies to keep your tests fast:

### 1. Strategic Ignore Patterns

For very large objects, ignoring irrelevant parts early in the comparison process can dramatically improve performance:

```scala mdoc:silent
case class LargeObject(
  id: String,
  metadata: Map[String, String], // Could contain hundreds of entries
  data: List[DataPoint],         // Could contain thousands of entries
  audit: AuditInfo               // Complex nested structure
)

implicit val largeObjectDiffer: Differ[LargeObject] = Differ.derived[LargeObject]

// If you only care about the id and data for a test:
val optimizedDiffer = largeObjectDiffer
  .ignoreAt(_.metadata) // Skip comparing the entire metadata map
  .ignoreAt(_.audit)    // Skip comparing the entire audit structure
```

This prevents Difflicious from recursively comparing large nested structures that aren't relevant to your test.

### 2. Custom Value Differs for Complex Types

For complex types with expensive equality checks, consider using a simplified differ:

```scala mdoc:silent
case class Vector3D(x: Double, y: Double, z: Double) {
  // Potentially expensive operations
  def magnitude: Double = Math.sqrt(x*x + y*y + z*z)
  def normalize: Vector3D = {
    val mag = magnitude
    Vector3D(x/mag, y/mag, z/mag)
  }
}

// Instead of using derived (which would compare all fields),
// create a custom differ that just compares what you need:
val vector3DDiffer = Differ.useEquals[Vector3D](v => 
  f"Vector3D(${v.x}%.2f, ${v.y}%.2f, ${v.z}%.2f)"
)
```

### 3. Profiling Diff Performance

If your tests are slow, you can profile where the time is spent:

```scala mdoc:silent
def timeDiff[A](differ: Differ[A], a1: A, a2: A): (Boolean, Long) = {
  val start = System.nanoTime()
  val result = differ.diff(a1, a2)
  val duration = System.nanoTime() - start
  (result.isOk, duration / 1000000) // Return success status and time in ms
}

// Use it to compare different differ configurations
val (result1, time1) = timeDiff(standardDiffer, obj1, obj2)
val (result2, time2) = timeDiff(optimizedDiffer, obj1, obj2)
println(s"Standard: $time1 ms, Optimized: $time2 ms")
```

### 4. Memory Usage Considerations

For tests involving very large objects, be mindful of memory usage:

- Break down large objects into smaller, more manageable pieces
- Focus your tests on specific substructures rather than entire object graphs
- Consider using streaming approaches for very large datasets

### 5. Batch Testing Strategy

When testing large datasets, consider a batched approach:

```scala
// Instead of testing all 10,000 items at once
val allItems: List[Item] = getVeryLargeItemList()

// Test in batches of 100
allItems.grouped(100).zipWithIndex.foreach { case (batch, index) =>
  test(s"Batch $index processes correctly") {
    val results = processItems(batch)
    val expected = expectedResults(batch)
    
    // This will be much faster than comparing the entire list
    Differ[List[Result]].assertNoDiff(results, expected)
  }
}
```

This approach not only improves performance but also makes it easier to identify which specific items are causing test failures.

## Frequently Asked Questions

### How can I provide a Differ for my newtypes / opaque types?

Many Scala users like to use a wrapper type around primitive types for additional type-safety.

All `ValueDiffer` has a `contramap` method you can use.

```scala mdoc:silent
final case class UserId(value: String)

val userIdDiffer: Differ[UserId] = Differ.stringDiffer.contramap(_.value)
```

Note that the type of Differ.stringDiffer is a `ValueDiffer` (`ValueDiffer` is a subtype of `Differ`)

### How do I compare collections regardless of order?

For lists or sequences where order doesn't matter, use `.pairBy` to match elements by a key:

```scala mdoc:silent
case class Item(id: String, value: Int)
implicit val itemDiffer: Differ[Item] = Differ.derived[Item]

// Compare lists of items by id, regardless of order
val orderIndependentDiffer = Differ[List[Item]].pairBy(_.id)
```

### How do I ignore certain fields in nested structures?

Use the path expression syntax with `.ignoreAt`:

```scala mdoc:silent
case class Address(street: String, city: String, zipCode: String)
case class Person(name: String, age: Int, address: Address)

implicit val addressDiffer: Differ[Address] = Differ.derived[Address]
implicit val personDiffer: Differ[Person] = Differ.derived[Person]

// Ignore the zipCode field in the address
val personDifferIgnoringZip = personDiffer.ignoreAt(_.address.zipCode)
```

### How do I handle cyclic references?

Difflicious doesn't directly support cyclic references. For types with cyclic references, you should:

1. Create a custom differ that breaks the cycle
2. Use `Differ.alwaysIgnore` for parts of the structure that might cause cycles

```scala mdoc:silent
// Example with potential cycles
case class Node(id: String, children: List[Node])

// Break the cycle with a custom differ
implicit lazy val nodeDiffer: Differ[Node] = {
  implicit lazy val listNodeDiffer: Differ[List[Node]] = {
    // This will recursively use treeNodeDiffer for elements
    Differ[List[Node]].pairBy(_.id)
  }
  
  Differ.derived[Node]
}
```

### How do I test with Difflicious in property-based tests?

Difflicious works well with property-based testing libraries like ScalaCheck:

```scala
import org.scalacheck.Prop
import org.scalacheck.Prop._

def testTransformation[A: Differ](input: A, transform: A => A): Prop = {
  val result = transform(input)
  val diffResult = Differ[A].diff(result, input)
  
  if (diffResult.isOk) passed
  else falsified :| diffResult.toString
}
```

### How do I create a custom Differ for a complex type?

For complex types where the default derived differ isn't suitable, you can create a custom differ that implements the required methods.

### How do I handle optional fields?

For optional fields (e.g., `Option[T]`), Difflicious handles them automatically:

```scala mdoc:silent
case class UserProfile(
  name: String,
  bio: Option[String],
  website: Option[String]
)

implicit val userProfileDiffer: Differ[UserProfile] = Differ.derived[UserProfile]

// Example usage
val profileResult = userProfileDiffer.diff(
  UserProfile("Alice", Some("Developer"), None),
  UserProfile("Alice", Some("Engineer"), Some("example.com"))
)
```

Output:

<pre class="diff-render">
UserProfile(
  name: "Alice",
  bio: Some(
    <span style="color: red;">"Developer"</span> -> <span style="color: green;">"Engineer"</span>
  ),
  website: <span style="color: red;">None</span> -> <span style="color: green;">Some(
    "example.com"
  )</span>
)
</pre>

## Advanced Topics

### Creating Differs for External Libraries

When working with external libraries, you might need to create differs for types you don't control:

```scala mdoc:silent
// Example for a hypothetical external library
object external {
  case class ExternalType(id: String, data: Map[String, String])
}

// Create a differ for the external type
implicit val externalTypeDiffer: Differ[external.ExternalType] = Differ.derived[external.ExternalType]
```

### Testing with Multiple Configurations

Sometimes you need to test the same values with different differ configurations:

```scala mdoc:silent
case class TestData(a: Int, b: String, c: Double)
implicit val testDataDiffer: Differ[TestData] = Differ.derived[TestData]

// Different configurations for different test scenarios
val strictDiffer = testDataDiffer
val lenientDiffer = testDataDiffer.ignoreAt(_.c)
val veryLenientDiffer = testDataDiffer.ignoreAt(_.b).ignoreAt(_.c)

// Use each differ for different test cases
```

### Debugging Differ Derivation

If you're having trouble with differ derivation, you can debug it by:

1. Breaking down the derivation into smaller pieces
2. Using explicit type annotations
3. Creating intermediate differs for nested types

```scala mdoc:silent
// Breaking down derivation for a complex type
case class Inner(x: Int, y: String)
case class Middle(inner: Inner, z: Double)
case class Outer(middle: Middle, w: Boolean)

// Step by step derivation
implicit val innerDiffer: Differ[Inner] = Differ.derived[Inner]
implicit val middleDiffer: Differ[Middle] = Differ.derived[Middle]
implicit val outerDiffer: Differ[Outer] = Differ.derived[Outer]
```

## Conclusion

By following these best practices and understanding how to address common scenarios, you can use Difflicious effectively to create clear, actionable test failures that make it easy to understand and fix issues in your code.

If you have questions not covered here, please [open an issue](https://github.com/jatcwang/difflicious/issues) on the Difflicious GitHub repository.

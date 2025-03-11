---
layout: docs
title:  "Configuring Differs"
permalink: docs/configuring-differs
---

# Configuring Differs

One of Difflicious' most powerful features is the ability to customize how comparisons work. This page explains how to configure differs to suit your specific testing needs.

## Basic Configuration Methods

Difflicious provides several ways to configure differs:

* **`ignoreAt`**: Ignore specific fields or elements in the comparison
* **`configure`**: Apply custom configuration to a part of a differ
* **`pairBy`**: Customize how collection elements are matched 
* **`replace`**: Replace a differ at a specific path with another differ

All these methods take a **path expression** that specifies which part of the data structure to configure.

## Path Expressions

Path expressions are used to navigate through data structures. They use a lambda syntax to specify the path:

```scala mdoc:silent
import difflicious._
import difflicious.implicits._

case class Address(street: String, city: String, zipCode: String)
case class Person(name: String, age: Int, address: Address)

implicit val addressDiffer: Differ[Address] = Differ.derived[Address]
implicit val personDiffer: Differ[Person] = Differ.derived[Person]

// Path to the city field inside address
personDiffer.ignoreAt(_.address.city)
```

The path expression `_.address.city` navigates from the root object (`_`) to the `address` field, then to the `city` field.

### Special Path Operations

In addition to normal field access, Difflicious provides special path operations:

#### `.each` for Collection Elements

The `.each` operation selects all elements in a collection:

```scala mdoc:silent
// Ignore the age field of each person in a list
val personListDiffer = Differ[List[Person]].ignoreAt(_.each.age)
```

When used with `ignoreAt`, this will ignore the specified field for every element in the collection.

#### `.subType` for Sealed Traits/Enums

The `.subType` operation selects a specific subtype of a sealed trait:

```scala mdoc:silent
sealed trait Shape
object Shape {
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  
  implicit val differ: Differ[Shape] = Differ.derived[Shape]
}

import Shape._

// Ignore the radius field for all Circle instances
val shapeListDiffer = Differ[List[Shape]].ignoreAt(_.each.subType[Circle].radius)
```

This allows you to configure how specific subtypes are compared.

## Ignoring Fields

The `ignoreAt` method is used to exclude specific fields from the comparison:

```scala mdoc:silent
// Ignore a specific field
val personDifferIgnoringAge = personDiffer.ignoreAt(_.age)

// Ignore a nested field
val personDifferIgnoringZipCode = personDiffer.ignoreAt(_.address.zipCode)

// Ignore multiple fields
val personDifferIgnoringMultiple = personDiffer
  .ignoreAt(_.age)
  .ignoreAt(_.address.zipCode)
```

### When to Ignore Fields

You might want to ignore fields when:

* They contain timestamps or other non-deterministic values
* They're irrelevant to the specific test scenario
* They contain complex nested structures that are tested separately

### Example: Testing with Timestamps

When testing data that includes timestamps, you often want to ignore them:

```scala mdoc:silent
case class Event(id: String, name: String, timestamp: Long)
implicit val eventDiffer: Differ[Event] = Differ.derived[Event]

// Ignore timestamps when comparing events
val eventDifferIgnoringTimestamp = eventDiffer.ignoreAt(_.timestamp)
```

## Configuring Collection Matching

By default, collections are compared by matching elements at the same index. However, this is often not what you want when the order doesn't matter or when you need to match elements based on a key.

### Using `pairBy` for Key-Based Matching

The `pairBy` method allows you to match elements based on a key function:

```scala mdoc:silent
case class User(id: String, name: String, email: String)
implicit val userDiffer: Differ[User] = Differ.derived[User]

// Match users by id, regardless of order in the list
val userListDiffer = Differ[List[User]].pairBy(_.id)
```

This will match users based on their `id` field, even if they appear in different orders in the obtained and expected lists.

### Using `pairByIndex` for Position-Based Matching

If you specifically want to match by position in the collection:

```scala mdoc:silent
// Explicitly match by index (this is the default behavior)
val indexMatchingDiffer = Differ[List[User]].pairByIndex
```

### Example: Testing API Responses

When testing API responses, you often need to ignore order:

```scala mdoc:silent
case class SearchResult(
  query: String,
  results: List[User],
  totalCount: Int
)
implicit val searchResultDiffer: Differ[SearchResult] = Differ.derived[SearchResult]

// Configure the differ to match users by id, regardless of order
val configuredSearchDiffer = searchResultDiffer.configure(_.results)(_.pairBy(_.id))
```

## Advanced Configuration Techniques

For complex use cases, you can combine Difflicious' configuration capabilities in powerful ways.

### Creating Domain-Specific Configuration DSLs

For applications with common configuration patterns, you can create extension methods:

```scala mdoc:silent
// Define extension methods for your domain
implicit class UserDifferOps(differ: Differ[User]) {
  // Predefined configurations for common use cases
  def ignorePersonalInfo: Differ[User] = 
    differ.ignoreAt(_.email).ignoreAt(_.address)
    
  def forAudit: Differ[User] = 
    differ.ignoreAt(_.lastLoginTime).ignoreAt(_.securityToken)
}

// Use them in your tests
val auditDiffer = userDiffer.forAudit
```

### Context-Dependent Configuration

You can create differs that adapt based on the context:

```scala mdoc:silent
// A differ that configures itself based on the environment
def contextAwareDiffer(env: String): Differ[ApiResponse] = {
  val base = Differ.derived[ApiResponse]
  
  env match {
    case "test" => 
      // In test, ignore non-deterministic fields
      base.ignoreAt(_.timestamp).ignoreAt(_.requestId)
    case "prod" => 
      // In prod, be more strict
      base
    case "staging" =>
      // In staging, use special configuration
      base.ignoreAt(_.debugInfo)
  }
}

// Use it in your tests
val differ = contextAwareDiffer(sys.env.getOrElse("ENV", "test"))
```

### Recursive Structure Configuration

For recursive data structures, you need careful configuration:

```scala mdoc:silent
// A recursive tree structure
case class TreeNode(value: String, children: List[TreeNode])

// Create a differ for it
implicit lazy val treeNodeDiffer: Differ[TreeNode] = {
  implicit lazy val listTreeNodeDiffer: Differ[List[TreeNode]] = {
    // This will recursively use treeNodeDiffer for elements
    Differ[List[TreeNode]].pairBy(_.value)
  }
  
  Differ.derived[TreeNode]
}

// Now you can compare tree structures
val tree1 = TreeNode("root", List(TreeNode("a", Nil), TreeNode("b", Nil)))
val tree2 = TreeNode("root", List(TreeNode("b", Nil), TreeNode("a", Nil)))
val result = treeNodeDiffer.diff(tree1, tree2) // Will match correctly despite order
```

### Configuration Composition

You can compose configurations to create reusable patterns:

```scala mdoc:silent
// Base configurations
def ignoreTimestamps[T](differ: Differ[T]): Differ[T] = {
  differ.configure(_.timestamp)(_.ignore)
        .configure(_.createdAt)(_.ignore)
        .configure(_.updatedAt)(_.ignore)
}

def ignoreIds[T](differ: Differ[T]): Differ[T] = {
  differ.configure(_.id)(_.ignore)
        .configure(_.uuid)(_.ignore)
}

// Compose them
def testingDiffer[T](differ: Differ[T]): Differ[T] = {
  ignoreTimestamps(ignoreIds(differ))
}

// Use the composed configuration
val testReadyDiffer = testingDiffer(Differ[MyEntity])
```

### Dynamic Configuration Based on Data

You can create configuration that adapts based on the data being compared:

```scala mdoc:silent
// Configure differently based on data characteristics
def smartDiffer(data1: DataSet, data2: DataSet): Differ[DataSet] = {
  val base = Differ.derived[DataSet]
  
  // Different strategies based on data size
  if (data1.size > 1000 || data2.size > 1000) {
    // For large datasets, use a more lenient comparison
    base.ignoreAt(_.metadata)
        .configure(_.items)(_.pairBy(_.id))
  } else {
    // For small datasets, be more strict
    base.configure(_.items)(_.pairByIndex)
  }
}
```

These advanced techniques allow you to create highly customized comparison strategies that adapt to your specific testing needs.

## Nested Collection Configuration

You can configure deeply nested collections:

```scala mdoc:silent
case class Department(name: String, employees: List[User])
case class Company(name: String, departments: List[Department])

implicit val departmentDiffer: Differ[Department] = Differ.derived[Department]
implicit val companyDiffer: Differ[Company] = Differ.derived[Company]

// Configure how to compare employees in each department
val configuredCompanyDiffer = companyDiffer
  .configure(_.departments)(_.pairBy(_.name))
  .configure(_.departments.each.employees)(_.pairBy(_.id))
```

This configures the differ to:
1. Match departments by name
2. For each department, match employees by id

## Replacing Differs

Sometimes you need to completely replace the differ used for a specific part of the structure:

```scala mdoc:silent
// A custom differ for users that only compares ids
val idOnlyUserDiffer = Differ.useEquals[User](user => s"User(id=${user.id})")

// Replace the employee differs with our custom one
val idOnlyEmployeesDiffer = companyDiffer.replace(_.departments.each.employees.each)(idOnlyUserDiffer)
```

## Example: Handling Complex Data Structures

For complex nested structures, you can use a combination of techniques:

```scala mdoc:silent
case class DeepNested(
  id: String,
  metadata: Map[String, List[String]],
  items: List[Map[String, User]]
)

implicit val deepNestedDiffer: Differ[DeepNested] = Differ.derived[DeepNested]

// Configure a complex structure
val configuredDeepDiffer = deepNestedDiffer
  .ignoreAt(_.metadata) // Ignore the entire metadata map
  .configure(_.items.each)(_.pairBy(map => map.keys.mkString(","))) // Match by keys
  .configure(_.items.each.each.value)(userDiffer.ignoreAt(_.email)) // Ignore emails in users
```

## Debugging Configuration Issues

If you're having trouble with differ configuration, here are some tips:

1. **Build up incrementally**: Start with simple configurations and gradually add complexity
2. **Check your path expressions**: Make sure the paths correctly navigate to the desired elements
3. **Use explicit type annotations**: This can help the compiler understand your intent

If you get a path error like `PathTooLong` or `NonExistentField`, check that your path expression matches the actual structure of your data.

## Conclusion

Configuring differs is a powerful way to focus your tests on the aspects you care about. By ignoring irrelevant fields and customizing how collections are matched, you can create more maintainable and readable tests.

Remember:
* Use `ignoreAt` to skip fields that aren't relevant to your test
* Use `pairBy` to match collection elements by key instead of position
* Use `.each` and `.subType` in your path expressions to navigate collections and sum types
* Combine multiple configurations for complex data structures

For more examples, check the [Cheatsheet](docs/cheatsheet) page.

```scala mdoc:invisible
import difflicious.Example._
```

Code examples in this page assumes the following import:
```scala mdoc:silent
import difflicious._
import difflicious.implicits._
```

## Basic Configuration

### Ignore and Unignore

You can call `.ignore` or `.unignore` on all Differs. This will ignore their diff results and stop it from failing tests.

### Pair By

For Differs of Seq/Set-like data structures, you can call `.pairBy` or `.pairByIndex` to change how elements of these 
data structures are paired up for comparison.

## Deep configuration using path expressions

Difflicious supports configuring a subpart of a Differ with a complex type by using `.configure` which takes a "path expression"
which you can use to express the path to the Differ you want to configure.

| Differ Type  | Allowed Paths          | Explanation                                                       |
| --           | --                     | --                                                                |
| Seq          | .each                  | Traverse down to the Differ used to compare the elements          |
| Set          | .each                  | Traverse down to the Differ used to compare the elements          |
| Map          | .each                  | Traverse down to the Differ used to compare the values of the Map |
| Case Class   | (any case class field) | Traverse down to the Differ for the specified sub type            |
| Sealed Trait | .subType[SomeSubType]  | Traverse down to the Differ for the specified sub type            |

Some examples:

```scala mdoc:invisible
sealed trait MySealedTrait
case class SomeSubType(fieldInSubType: String) extends MySealedTrait

object MySealedTrait {
  implicit val differ: Differ[MySealedTrait] = Differ.derived[MySealedTrait]
}
```

```scala mdoc:nest:silent
val differ: Differ[Map[String, List[Person]]] = Differ[Map[String, List[Person]]]

// Don't fail if peron's name is different.
val differIgnoringPersonName = differ.ignoreAt(_.each.each.name)
// .ignoreAt is just a shorthand for configure(...)(_.ignore) so this is equivalent
val differIgnoringPersonName2 = differ.configure(_.each.each.name)(_.ignore)

// When comparing List[Person], pair the elements by the Person's name
val differPairingByPersonName = differ.configure(_.each)(_.pairBy(_.name))

// "Focusing" into the Differ for a subtype and ignoring a field
val sealedTraitDiffer: Differ[List[MySealedTrait]] = Differ[List[MySealedTrait]]
val differWithSubTypesFieldIgnored = sealedTraitDiffer.ignoreAt(_.each.subType[SomeSubType].fieldInSubType)
```

## Replace differs

You can completely replace the underlying differ at a path using `replace`. This is useful when you want to reuse an existing
Differ you already have.

```scala mdoc:silent
val mapDiffer: Differ[Map[String, List[Person]]] = Differ[Map[String, List[Person]]]
val pairAndCompareByAge = Differ[List[Person]].pairBy(_.age).ignoreAt(_.each.name)
val pairByName = Differ[List[Person]].pairBy(_.name)

// Use this to compare each person list by age only
mapDiffer.replace(_.each)(pairAndCompareByAge)

// Use this to compare each person list paired by name
mapDiffer.replace(_.each)(pairByName)
```

## Unsafe API with `configureRaw`

This is a low-level API that you shouldn't need in normal usage. All the nice in the previous sections calls this 
under the hood and it is exposed in case you really need it.

`configureRaw` takes a stringly-typed path to configure the Differ and a raw `ConfigureOp`.
While the API tries to detect errors, there is very little type safety and mistakes can lead to runtime exception.
(For example, `configureRaw` won't stop you from replacing a Differ with a Differ of the wrong type)

```scala
def configureRaw(path: ConfigurePath, operation: ConfigureOp): Either[DifferUpdateError, Differ[T]]
```

We need to provide:

- A `path` parameter to "travsere" to the Differ you want to cnofigure. Can be the current Differ (`ConfigurePath.current`), or a Differ embedded inside it.
- The type of configuration change you want to make e.g. Mark the Differ as `ignored`

Let's look at some examples:

```scala mdoc:silent
import difflicious.{Differ, ConfigureOp, ConfigurePath}
```

**Example: Changing diff of `List[Person]` to pair elements by `name` field**

Let's say we want to compare the `List[Person]` independent of element order but instead match by `name` field...

```scala mdoc:silent
val defaultDiffer: Differ[Map[String, List[Person]]] = Differ[Map[String, List[Person]]]
val differPairByName: Differ[Map[String, List[Person]]] = defaultDiffer
  .configureRaw(
    ConfigurePath.of("each"), 
    ConfigureOp.PairBy.ByFunc[Person, String](_.name)
  ).right.get
  
// Try it!  
differPairByName.diff(
  Map(
    "Germany" -> List(
      Person("Bob", 55),
      Person("Alice", 55),
    )
  ),
  Map(
    "Germany" -> List(
      Person("Alice", 56),
      Person("Bob", 55),
    ),
    "France" -> List.empty
  )
)
```

<pre class="diff-render">
Map(
  "Germany" -> List(
      Person(
        name: "Bob",
        age: 55,
      ),
      Person(
        name: "Alice",
        age: <span style="color: red;">55</span> -> <span style="color: green;">56</span>,
      ),
    ),
  <span style="color: green;">"France"</span> -> <span style="color: green;">List(
    )</span>,
)
</pre>

**Example: Ignore a field in a Person when comparing**

Let's say we don't want to take into account the name of the person when comparing...

```scala mdoc:silent
val differPersonAgeIgnored: Differ[Map[String, List[Person]]] = defaultDiffer
  .configureRaw(
    ConfigurePath.of("each", "each", "age"), 
    ConfigureOp.ignore
  ).right.get
  
// Try it!  
differPersonAgeIgnored.diff(
  Map(
    "Germany" -> List(
      Person("Alice", 55),
      Person("Bob", 55),
    )
  ),
  Map(
    "Germany" -> List(
      Person("Alice", 100),
      Person("Bob", 100),
    ),
  )
)
```

<pre class="diff-render">
Map(
  "Germany" -> List(
      Person(
        name: "Alice",
        age: <span style="color: gray;">[IGNORED]</span>,
      ),
      Person(
        name: "Bob",
        age: <span style="color: gray;">[IGNORED]</span>,
      ),
    ),
)
</pre>

When testing (e.g. assertNoDiff) the test would pass because the person's age is not considered in the comparison.

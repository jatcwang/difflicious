---
layout: docs
title:  "Quickstart"
permalink: docs/quickstart
---

# Quickstart

This guide will help you get up and running with Difflicious in your test suite.

## Installation

First, add Difflicious to your build. Choose the appropriate integration for your test framework:

### For MUnit

```scala
// SBT
libraryDependencies += "com.github.jatcwang" %% "difflicious-munit" % "{{ site.version }}" % Test

// Mill
ivy"com.github.jatcwang::difflicious-munit:{{ site.version }}"
```

### For ScalaTest

```scala
// SBT
libraryDependencies += "com.github.jatcwang" %% "difflicious-scalatest" % "{{ site.version }}" % Test

// Mill
ivy"com.github.jatcwang::difflicious-scalatest:{{ site.version }}"
```

### For Weaver

```scala
// SBT
libraryDependencies += "com.github.jatcwang" %% "difflicious-weaver" % "{{ site.version }}" % Test

// Mill
ivy"com.github.jatcwang::difflicious-weaver:{{ site.version }}"
```

## IDE Setup

If you are running tests using **IntelliJ IDEA**'s test runner, you will want 
to turn off the red text coloring it uses for test failure outputs because
it interferes with difflicious' color outputs.

In <b>File | Settings | Editor | Color Scheme | Console Colors | Console | Error Output</b>, uncheck the red foreground color.

## Basic Usage

Let's walk through a simple example using Difflicious with MUnit:

```scala mdoc:silent
import munit.FunSuite
import difflicious.Differ
import difflicious.munit.MUnitDiff._

// Define a simple case class
case class Person(
  name: String,
  age: Int,
  occupation: String
)

// Create a test suite
class ExampleTest extends FunSuite {

  // Derive a Differ for the Person class
  implicit val personDiffer: Differ[Person] = Differ.derived[Person]

  test("two list of people should be the same") {
    // Create a differ for List[Person]
    val differ = Differ[List[Person]]
    
    // Use assertNoDiff to compare values
    differ.assertNoDiff(
      List(
        Person("Alice", 50, "Doctor")
      ),
      List(
        Person("Alice", 40, "Doctor"),
        Person("Bob", 30, "Teacher")
      )
    )
  }
}
```

When this test runs, it will fail with a nicely formatted diff output:

<pre class="diff-render">
List(
  Person(
    name: "Alice",
    age: <span style="color: red;">50</span> -> <span style="color: green;">40</span>,
    occupation: "Doctor"
  ),
  <span style="color: green;">Person(
    name: "Bob",
    age: 30,
    occupation: "Teacher"
  )</span>
)
</pre>

The diff clearly shows that:
1. Alice's age is different (50 in the actual result, 40 in the expected result)
2. Bob is missing from the actual result (shown in green as it only exists in the expected result)

## Customizing Differs

Difflicious allows you to customize how values are compared. Here's an example:

```scala mdoc:silent
import difflicious.implicits._

test("compare people by name only") {
  // Create a differ that pairs people by name and ignores age
  val customDiffer = Differ[List[Person]]
    .pairBy(_.name)  // Match people by name
    .ignoreAt(_.each.age)  // Ignore age differences
  
  customDiffer.assertNoDiff(
    List(
      Person("Alice", 50, "Doctor"),
      Person("Bob", 25, "Engineer")
    ),
    List(
      Person("Alice", 40, "Doctor"),
      Person("Bob", 30, "Teacher")
    )
  )
}
```

This test will fail because Bob's occupation is different, but it will ignore the age differences:

<pre class="diff-render">
List(
  Person(
    name: "Alice",
    age: <span style="color: gray;">[IGNORED]</span>,
    occupation: "Doctor"
  ),
  Person(
    name: "Bob",
    age: <span style="color: gray;">[IGNORED]</span>,
    occupation: <span style="color: red;">"Engineer"</span> -> <span style="color: green;">"Teacher"</span>
  )
)
</pre>

## Framework-Specific Examples

### ScalaTest

```scala
import org.scalatest.funsuite.AnyFunSuite
import difflicious.Differ
import difflicious.scalatest.ScalatestDiff._

class ExampleScalatestTest extends AnyFunSuite {
  implicit val personDiffer: Differ[Person] = Differ.derived[Person]
  
  test("people should match") {
    Differ[List[Person]].assertNoDiff(
      List(Person("Alice", 30, "Doctor")),
      List(Person("Alice", 30, "Doctor"))
    )
  }
}
```

### Weaver

```scala
import weaver.SimpleIOSuite
import difflicious.Differ
import difflicious.weaver.WeaverDiff._

object ExampleWeaverTest extends SimpleIOSuite {
  implicit val personDiffer: Differ[Person] = Differ.derived[Person]
  
  pureTest("people should match") {
    Differ[List[Person]].assertNoDiff(
      List(Person("Alice", 30, "Doctor")),
      List(Person("Alice", 30, "Doctor"))
    )
  }
}
```

## Troubleshooting

### Colors Not Showing in Output

If you don't see colors in your test output:

1. Make sure your terminal supports ANSI colors
2. For IntelliJ, check the IDE setup section above
3. For CI environments, ensure the CI configuration allows colored output

### "Could not find implicit value for parameter differ"

This error means Difflicious couldn't find a `Differ` instance for your type. You need to either:

1. Import an existing instance: `import difflicious.implicits._`
2. Create your own: `implicit val myDiffer: Differ[MyType] = Differ.derived[MyType]`

### Comparing Complex Nested Structures

For complex nested structures, you might want to configure differs at multiple levels:

```scala
case class Address(street: String, city: String)
case class User(name: String, address: Address)

val differ = Differ[List[User]]
  .pairBy(_.name)
  .ignoreAt(_.each.address.street)
```

## Next Steps

Now that you're up and running with Difflicious, check out:

- [Types of Differs](docs/types-of-differs) to learn about the different types of differs available
- [Configuring Differs](docs/configuring-differs) to learn how to customize differs for your needs
- [Best Practices](docs/best-practices-and-faq) for tips on using Difflicious effectively

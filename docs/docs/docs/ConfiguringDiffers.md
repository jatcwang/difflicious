---
layout: docs
title:  "Configuring Differs"
permalink: docs/configuring-differs
---

# Configuring Differs

In Difflicious, Differs are built to be reconfigurable, allowing you to adapt an existing Differ for each test as needed.

Here are some examples of what difflicious allows you to do:

- Compare two `Person` normally, except to compare the `wallet: List[Coin]` field disregarding the order of coins
- Ignore the person's age when comparing `Map[String, Person]`
  
Differ configuration is done using the `configureRaw` method:

```scala
def configureRaw(path: ConfigurePath, operation: ConfigureOp): Either[DifferUpdateError, Differ[T]]
```

We need to provide:

- A `path` to "travsere" to the Differ you want to cnofigure. Can be the current Differ (`ConfigurePath.current`), or a Differ embedded inside it.
- The type of configuration change you want to make e.g. Mark the Differ as `ignored`

## Anatomy of a Differ

Most Differs you use will be for comparing complex types. These complex Differs are made up of smaller Differs.

Let's say we have a complex differ `Differ[Map[String, List[Person]]]`, here's a visualization of what it's made up of:

```
Differ[Map[String, List[Person]]]:
  │
  └ Differ[List[Person]]
     │
     └ Differ[Person]
        │
        ├ Differ[String] (for the "name" field)
        └ Differ[Int]    (for the "age" field)
```

With `configure` method, you can "traverse" to a Differ within another Differ in order to "tweak" it. 
To locate the Differ, You need to provide the `path`.

```
Differ[Map[String, List[Person]]]:
  │
  └ each: Differ[List[Person]]
     │
     └ each: Differ[Person]subject
        │
        ├ name: Differ[String] 
        └ age:  Differ[Int]    
```

For example, if I want to ignore a person's name when comparing, the path will be `ConfigurePath.of("each", "each", "name")`

**"each"** is a special path to refer to the underlying Differ for a `Map`, `Set` or `Seq` Differ.

## Using `configureRaw`

With `configureRaw` you pass a "stringly-typed" path to configure the Differ, so unfortunately you won't get much help from the compiler.
But don't worry! types are still checked at runtime thanks to [izumi-reflect](https://github.com/zio/izumi-reflect) 

In the future, we will provide a nicer API on top of `configureRaw`, similar to the API of 
[quicklens](https://github.com/softwaremill/quicklens)

Let's look at some examples:

```scala mdoc:invisible
// FIXME:
import difflicious.Example.printHtml
```

```scala mdoc:silent
import difflicious.{Differ, ConfigureOp, ConfigurePath}

final case class Person(name: String, age: Int)

object Person {
  implicit val differ: Differ[Person] = Differ.derive[Person]
}

val defaultDiffer: Differ[Map[String, List[Person]]] = Differ[Map[String, List[Person]]]
```

**Example: Changing diff of `List[Person]` to pair elements by `name` field**

Let's say we want to compare the `List[Person]` independent of element order but instead match by `name` field...

```scala mdoc:silent
val differPairByName: Differ[Map[String, List[Person]]] = defaultDiffer
  .configureRaw(
    ConfigurePath.of("each"), 
    ConfigureOp.PairBy.func((p: Person) => p.name)
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

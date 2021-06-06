---
layout: docs
title:  "Configuring Differs"
permalink: docs/configuring-differs
---

# Configuring Differs

In Difflicious, Differs are built to be reconfigurable. This allow you to adapt an existing Differ for each test 
as needed.

Here are some examples of what difflicious allows you to do:

- Compare two `Person` normally, except to compare the `wallet: List[Coin]` field disregarding the order of coins
- Ignore `age` field when comparing two `Person` values

Difflicious also supports deep configuration where you can tweak how a particular sub-structure of a type is compared,
with an intuitive API similar to the ones found in libraries like [Quicklens](https://github.com/softwaremill/quicklens) 
and [Monocle](https://www.optics.dev/Monocle/).

Configuring a Differ creates a new Differ instead of mutating the existing instance.

```scala mdoc:invisible
import difflicious._
import difflicious.implicits._
import difflicious.Example._
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

| Differ Type  | Allowed Paths           | Explanation                                                       | 
| --           | --                      | --                                                                | 
| Seq          | `.each`                 | Traverse down to the Differ used to compare the elements      | 
| Set          | `.each`                 | Traverse down to the Differ used to compare the elements      | 
| Map          | `.each`                 | Traverse down to the Differ used to compare the values of the Map | 
| Case Class   | (any case class field)  | Traverse down to the Differ for the specified sub type            | 
| Sealed Trait | `.subType[SomeSubType]` | Traverse down to the Differ for the specified sub type            | 

Some examples:

```scala mdoc:invisible
sealed trait MySealedTrait
case class SomeSubType(fieldInSubType: String) extends MySealedTrait

object MySealedTrait {
  implicit val differ: Differ[MySealedTrait] = Differ.derive[MySealedTrait]
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

## Unsafe API with `configureRaw`

This is a low-level API that you shouldn't really need in 99% of the cases. 
(Pleaes raise an issue if you feel like you shouldn't need to but was forced :))

`configureRaw` takes a stringly-typed path to configure the Differ and a raw `ConfigureOp`.
You won't get much help from the compiler here, but don't worry! types are still checked at runtime thanks to [izumi-reflect](https://github.com/zio/izumi-reflect) 

```scala
def configureRaw(path: ConfigurePath, operation: ConfigureOp): Either[DifferUpdateError, Differ[T]]
```

We need to provide:

- A `path` to "travsere" to the Differ you want to cnofigure. Can be the current Differ (`ConfigurePath.current`), or a Differ embedded inside it.
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
    ConfigureOp.PairBy.ByFunc[Person, String](_.name, implicitly)
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

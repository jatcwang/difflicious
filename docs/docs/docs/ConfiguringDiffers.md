---
layout: docs
title:  "Configuring Differs"
permalink: docs/configuring-differs
---

# Configuring Differs

In Difflicious, Differs are built to be reconfigurable. This allows you to adapt an existing Differ for each test 
as needed.

Difflicious also supports "deep configuration" where you can tweak how a particular sub-structure of a type is compared.
with an intuitive API similar to the ones found in libraries like [diffx](https://github.com/softwaremill/diffx) and 
[Quicklens](https://github.com/softwaremill/quicklens).

Differs are **immutable** - if you configure it it'll return a new Differ.

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

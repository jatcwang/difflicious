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
     └ each: Differ[Person]
        │
        ├ name: Differ[String] 
        └ age:  Differ[Int]    
```

For example, if I want to ignore a person's name when comparing, the path will be `ConfigurePath.of("each", "each", "name")`

**"each"** is a special path to refer to the underlying Differ for a `Map`, `Set` or `Seq` Differ.

## Using `configureRaw`

`configureRaw` is the "stringly-typed" way of configuring a Differ, so unfortunately you won't get much help from the compiler.
But don't worry! types are still checked at runtime thanks to [izumi-reflect](https://github.com/zio/izumi-reflect) 

In the future, we will provide a nicer API on top of `configureRaw`, similar to the API of 
[quicklens](https://github.com/softwaremill/quicklens)


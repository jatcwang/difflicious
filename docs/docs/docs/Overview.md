---
layout: docs
title:  "Library Overview"
permalink: docs/overview
---

# Library Overview

The central concept of Difflicious is `Differ[A]`.

What can you do with a `Differ[A]`?

- It can compare two values of type `A` with the `diff` method
- Its diffing logic can be changed using `configure` method

```scala
trait Differ[T] {
  def diff(inputs: DiffInput[T]): DiffResult

  def configure(path: ConfigurePath, operation: ConfigureOp): Either[DifferUpdateError, Differ[T]]
}
```

// FIXME: Ior?
You might be wondering why `diff` method takes a single `Ior[A]` value instead of two `A` parameters. 
This is because often you will only have one side of the value. 

Imagine comparing two `List[String]`

```
val obtainedList = List("a", "b")
val expectedList = List("a", "b", "c")
```

When diffing, we will pair up elements from both lists by index and diff each pair. 
However, in `expectedList` we have a third element `"c"` which does not have a corresponding value we can compare with.

So when comparing the third element, our diff result will be "expected only" i.e. only **expected** side has a value

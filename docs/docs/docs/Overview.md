---
layout: docs
title:  "Library Overview"
permalink: docs/overview
---

// FIXME: this should be renamed to internals

# Library Overview

The central concept of Difflicious is `Differ[A]`.

What can you do with a `Differ[A]`?

- It can compare two values of type `A` with the `diff` method
- Its diffing logic can be changed using `configure` method

```scala
trait Differ[T] {
  def diff(inputs: DiffInput[T]): DiffResult

  def configureRaw(path: ConfigurePath, operation: ConfigureOp): Either[DifferUpdateError, Differ[T]]
}
```

What is this `DifferInput` parameter in the `diff` method?
This is because when comparing two values, we often only have on side of the comparison - the other side is missing.

For example, let's comparing two `List[String]`

```
val obtainedList = List("a", "b")
val expectedList = List("a", "b", "c")
```

When diffing, we will pair up elements from both lists by index and diff each pair. 
However, in `expectedList` we have a third element `"c"` which does not have a corresponding value we can pair with for comparison.

So when "diffing" the third element, our diff result will be "expected only" i.e. only the **expected** side of the input is provided.

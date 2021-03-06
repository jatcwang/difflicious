---
layout: docs
title:  "Best Practices and Frequently Asked Questions"
permalink: docs/best-practices-and-faq
---

# Best Practices

## Managing `Differ` instances

Tests are often the last check for the correctness of your program before it gets deployed, so we need to 

Here are some tips to help you best manage `Differ` instances when using difflicious for testing:

* Only put unmodified derived Differ instances in the implicit scope
  * This avoids the scenario where a modified Differ is pulled in accidentally during derivation, which can results in 
    passing tests that otherwise should fail.
* If you need a modified Differ instance to be used in a derivation, scope it locally

```scala
object DifferInstances {
  implicit val personDiffer: Differ[Person] = Differ.derived[Person]
  
  val personByNameSeqDiffer: Differ[List[Person]] = Differ[List[Person]].pairBy(_.name)
}

// ...Somewhere else
val schoolDiffer: Differ[School] = {
  implicit val personByNameSeqDiffer: Differ[List[Person]] = DifferInstances.personByNameSeqDiffer
  Differ.derived[School]
}
```

# Frequently Asked Questions

## Where is fully automatic derivation for `Differ`s?

Fully automatic derivation is intentionally left out for compile time reasons.

With automatic derivation, the compiler will derive the instances **every time it is needed**.
This very frequently leads to extremely long compile times which isn't worth the few lines of code it saves you.

## How is difflicious different from other projects that provides diffs?

**MUnit**: MUnit's `assertEquals` comes out of the box with diff output for case classes. Users do not need to do anything 
to get the diff output due to its simplicity it isn't really configurable. It is a good idea to start with MUnit's `assertEquals`
and only use Difflicious when you need its configurability for more complex assertion failures.

**DiffX**: DiffX is one of the inspirations of this library and Difflicious aims to support all DiffX features/use cases.

Feature-wise, difflicious has:

- Better collection diffing: Difflicious allows you to specify how Seq/Set elements are paired for comparison. 
  Pairing also allows you compare Seqs order-independently.
- Better configurability: Difflicious takes a more "structured" approach to configurability, where Differ of a complex type
  can still have all its underlying Differs tweaked or even replaced (using `replace`). This is handy in some scenarios
  where you can reuse existing Differs by "swapping" them in and out of a larger Differ.
  
On the other hand, diffx has:

- Opt-in fully automatic derivation, if you want to convenience and willing to accept longer compile times.

**Note**: Diffx is an actively maintained library, so some comparison may not be up to date and corrections are welcome :)

## How can I provide a Differ for my newtypes / opaque types?

Many Scala users like to use a wrapper type around primitive types for additional type-safety.

All `ValueDiffer` has a `contramap` method you can use.

```scala mdoc:invisible
import difflicious._
```

```scala mdoc:silent
final case class UserId(value: String)

val userIdDiffer: Differ[UserId] = Differ.stringDiffer.contramap(_.value)
```

Note that the type of Differ.stringDiffer is a `ValueDiffer` (`ValueDiffer` is a subtype of `Differ`)


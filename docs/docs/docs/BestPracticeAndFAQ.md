---
layout: docs
title:  "Best Practices and Frequently Asked Questions"
permalink: docs/faq
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
  implicit val personDiffer: Differ[Person] = Differ.derive[Person]
  
  val personByNameSeqDiffer: Differ[List[Person]] = Differ[List[Person]].pairBy(_.name)
}

// ...Somewhere else
val schoolDiffer: Differ[School] = {
  implicit val personByNameSeqDiffer: Differ[List[Person]] = DifferInstances.personByNameSeqDiffer
  Differ.derive[School]
}
```

# Frequently Asked Questions

## Where is fully automatic derivation for `Differ`s?

Fully automatic derivation is intentionally left out for compile time reasons.

With automatic derivation, the compiler will derive the instances **every time it is needed**.
This very frequently leads to extremely long compile times which isn't worth the few lines of code it saves you.

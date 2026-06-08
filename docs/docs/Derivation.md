---
id: derivation
title: Derivation
---

# Derivation

This page covers the ways to create `Differ` instances for case classes and enums (sealed traits).

# Derivation for case class and enums (sealed traits)

Difflicious supports three derivation strategies for deriving instances for case class and enums (sealed traits)

- `Differ.derived[T]`, requires `Differ` instances for all field types.
- `Differ.derivedDeep[T]` derives a `Differ[T]` and also automatically derives Differ instance for any field types that doesn't already have one.
- Fully automatic derivation via `import difflicious.generic.auto.given` will automatically re-derive a Differ instance whenever it is needed

In all derivation strategies, if there's an existing Differ instance in the implicit scope, it will be reused.

Our recommendation is to start with `Differ.derivedDeep`, and use `Differ.derived` if you want better control over when derivations happen.
For quick experiments you can use automatic derivation, but it is not recommended as it can make code hard to follow especially when you have customized `Differ` instances.

## Debugging derivation

To inspect the derivation chain and see which instances are derived and which are source from implicit scope, 
you can import `difflicious.debug.implicits.logDerivation` which will diagnostics to logged for all derivation in scope.

```scala
import difflicious.debug.implicits.logDerivation

implicit val personDiffer: Differ[Person] = Differ.derivedDeep[Person]
```

If you also want to know how much time is spent deriving each instance, enable Hearth's benchmark-scope macro setting:

```scala
Compile / scalacOptions ++= Seq("-Xmacro-settings:hearth.mioBenchmarkScopes=true")
```

Read more about benchmarking in Hearth's [documentation here](https://scala-hearth.readthedocs.io/en/latest/micro-fp/#benchmarking-scopes-and-flame-graphs), 
including how to generate flame graphs if you need them.

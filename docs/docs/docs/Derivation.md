---
layout: docs
title:  "Derivation"
permalink: docs/derivation
---

# Derivation

This page covers the ways to create `Differ` instances for case classes and enums (sealed traits).

# Derivation for case class and enums (sealed traits)

Difflicious supports three derivation strategies for deriving instances for case class and enums (sealed traits)

- `Differ.derived[T]`, requires `Differ` instances for all field types.
- `Differ.derivedDeep[T]` derives a `Differ[T]` and also automatically derives Differ instance for any field types that doesn't already have one.
- Fully automatic derivation via `import difflicious.generic.auto.given` will automatically re-derive a Differ instance whenever it is needed

In all derivation strategies, if there's an existing Differ instance in the implicit scope, it will be reused.

## Comparison of derivation strategies

| Strategy | Compile time | Allocation | Verbosity |
| --- | --- | --- | --- |
| `Differ.derived[T]` | Probably lowest and more predictable. It derives only `T` and summons the field or subtype instances already in scope. | Predictable. There's only one Differ instance for each type | High, as you need to create a Differ for each type |
| `Differ.derivedDeep[T]` | Potentially higher than `derived` - you may end up deriving Differ for the same type multiple times if it is used in multiple places | Better than automatic derivation. Existing instances are reused, and missing intermediate instances are generated from the explicit call site. | Medium - you can avoid defining Differ instances for all intermediate types |
| Fully automatic derivation | High - derivation is performed every time you request for a Differ instance | High - no Differ instance reuse unless you define them manually | Lowest - you just ask for a `Differ[T]` :) |

Our recommendation is to start with `Differ.derivedDeep`, and use `Differ.derived` for better control.
For quick experiments you can use automatic derivation, but it is not recommended for code you want to keep - The compile time drag on the codebase is generally not worth the small convenience.

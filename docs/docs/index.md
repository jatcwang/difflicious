---
id: index
title: Difflicious
sidebar_label: Overview
---

# Difflicious

> Diffs for human and machine consumption

[![Release](https://img.shields.io/maven-central/v/com.github.jatcwang/difflicious-munit_3)](https://repo1.maven.org/maven2/com/github/jatcwang/difflicious-munit_3/)

**Difflicious** provides diffs for structured data in Scala, primarily for tests.

- Readable and actionable diff results tells you where exactly your data is different
- Flexible and configurable diffing logic
  - Ignore unimportant fields when comparing
  - Compare `List`s of items independent of order
  - Match `Map` entries by key and show diffs of the values
- Integration with test frameworks and popular libraries

Hungry for some good diffs? Check out the [documentation](https://jatcwang.github.io/difflicious/)!

# Installation

See [Quickstart](https://jatcwang.github.io/difflicious/docs/quickstart).

# Contributing

All contributions are welcome, including suggestions and ideas. For larger changes, please raise an issue
first to avoid duplicate work :)

# Attributions

This project takes many inspirations from:

- [diffx](https://github.com/softwaremill/diffx)'s path expression for ignoring fields
- [MUnit](https://scalameta.org/munit/)'s case class diffs

# License

**Apache License 2.0**. See LICENSE file.

# Difflicious

> Diffs for human consumption

[![Release](https://img.shields.io/nexus/r/com.github.jatcwang/difflicious-munit_2.13?server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/releases/com/github/jatcwang/difflicious-munit_2.13/)
[![(https://badges.gitter.im/gitterHQ/gitter.png)](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jatcwang/difflicious)

**Difflicious** is a library that produces nice readable diffs in your tests.

- Readable and Actionable diff results
- Flexible & Configurable diffing logic
  - Ignore unimportant fields when comparing
  - Compare `List`s of items independent of order
  - Match `Map` entries by key and show diffs of the values
- Integration with test frameworks and popular libraries

Hungry for some good diffs? Check out the [documentation](https://jatcwang.github.io/difflicious/)!

# Attributions & Inspirations

This project takes many inspirations from 

- [diffx](https://github.com/softwaremill/diffx)'s path expression for ignoring fields
- [MUnit](https://scalameta.org/munit/)'s case class diffs

# License

**Apache License 2.0**. See LICENSE file.

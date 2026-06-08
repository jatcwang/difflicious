---
id: circe
title: Circe
---

# Circe

Differ instances for Circe `Json` and `JsonObject` can be found in

```
"com.github.jatcwang" %% "difflicious-circe" % "@VERSION@" % Test
```

Import `difflicious.circe.*` to bring difflicious circe support into scope:

```scala mdoc:silent
import difflicious.circe.{*, given}

import difflicious.*
import difflicious.implicits.*
import io.circe.{Json, JsonObject}
```

```scala mdoc:invisible
import io.circe.literal.*
import difflicious.Example.diffHtml
```

## Differ for `io.circe.Json` and `io.circe.JsonObject`

Use `Differ[Json]` to diff `io.circe.Json` values

- JSON objects are diffed like `Map`
- JSON arrays are diffed like `Seq`

### `Differ[Json]` example
```scala mdoc:silent
val nestedJsonDiff = Differ[Json].diff(
  json"""
  {
    "name": "Alice",
    "age": 30,
    "tags": ["admin", "active"]
  }
  """,
  json"""
  {
    "name": "Alice",
    "age": 31,
    "tags": ["admin", "inactive"]
  }
  """,
)
```

```scala mdoc:passthrough
println(diffHtml(nestedJsonDiff))
```

### `Differ[JsonObject]` example
```scala mdoc:silent
val jsonObjectDiff = Differ[JsonObject].diff(
  JsonObject("name" -> Json.fromString("Alice")),
  JsonObject("name" -> Json.fromString("Bob")),
)
```

```scala mdoc:passthrough
println(diffHtml(jsonObjectDiff))
```

## Configuring JSON differs

Circe's internal JSON AST are public, so difflicious provides marker types for path configuration:
`JNull`, `JBoolean`, `JNumber`, `JString`, and `JArray`.

```scala mdoc:silent
val ignoreJsonNumbers: Differ[Json] =
  Differ[Json].ignoreAt(_.subType[JNumber])

val ignoreNumbersInsideJsonArrays: Differ[Json] =
  Differ[Json].ignoreAt(_.subType[JArray].each.subType[JNumber])

val ignoreNumbersInsideJsonObjects: Differ[Json] =
  Differ[Json].ignoreAt(_.subType[JsonObject].each.subType[JNumber])
```

```scala mdoc:silent
val ignoredJsonObjectNumberDiff = ignoreNumbersInsideJsonObjects.diff(
  json"""
    {"age": 30, "name": "Alice"}
  """,
  json"""
    {"age": 31, "name": "Bob"}
  """,
)
```

```scala mdoc:passthrough
println(diffHtml(ignoredJsonObjectNumberDiff))
```

A `Differ[JsonObject]` can also be configured using `.each`

```scala mdoc:silent
Differ[JsonObject].ignoreAt(_.each.subType[JNumber])
```

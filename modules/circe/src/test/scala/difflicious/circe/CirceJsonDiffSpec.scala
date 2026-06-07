package difflicious.circe

import difflicious.*
import difflicious.implicits.*
import difflicious.internal.EitherGetSyntax.*
import difflicious.testutils.*
import io.circe.{Json, JsonObject}
import munit.FunSuite

class CirceJsonDiffSpec extends FunSuite {

  test("Json: diffs same-case object values") {
    assertConsoleDiffOutput(
      Differ[Json],
      Json.obj("name" -> Json.fromString("Alice")),
      Json.obj("name" -> Json.fromString("Bob")),
      s"""JsonObject(
         |  "name" -> $R"Alice"$X -> $G"Bob"$X
         |)""".stripMargin,
    )
  }

  test("Json: diffs same-case array values") {
    assertConsoleDiffOutput(
      Differ[Json],
      Json.arr(Json.fromInt(1)),
      Json.arr(Json.fromInt(2)),
      s"""JArray(
         |  ${R}1$X -> ${G}2$X
         |)""".stripMargin,
    )
  }

  test("Json: diffs same-case number values") {
    assertConsoleDiffOutput(
      Differ[Json],
      Json.fromBigDecimal(BigDecimal("1.25")),
      Json.fromBigDecimal(BigDecimal("2.5")),
      s"${R}1.25$X -> ${G}2.5$X",
    )
  }

  test("Json: reports same-case equal number values as ok") {
    val result = Differ[Json].diff(Json.fromInt(123), Json.fromLong(123L))

    assert(result.isOk)
    result match {
      case value: DiffResult.ValueResult.Both =>
        assertEquals(value.obtained, "123")
        assertEquals(value.expected, "123")
        assert(value.isSame)
      case other => fail(s"Expected ValueResult.Both, got $other")
    }
  }

  test("Json: diffs object keys that only exist on one side") {
    assertConsoleDiffOutput(
      Differ[Json],
      Json.obj(
        "same" -> Json.fromString("x"),
        "removed" -> Json.fromInt(1),
        "changed" -> Json.fromBoolean(true),
      ),
      Json.obj(
        "same" -> Json.fromString("x"),
        "added" -> Json.Null,
        "changed" -> Json.fromBoolean(false),
      ),
      s"""JsonObject(
         |  ${R}"removed"$X -> ${R}1$X,
         |  "same" -> "x",
         |  "changed" -> ${R}true$X -> ${G}false$X,
         |  ${G}"added"$X -> ${G}Null$X
         |)""".stripMargin,
    )
  }

  test("Json: diffs nested object and array values") {
    assertConsoleDiffOutput(
      Differ[Json],
      Json.obj(
        "user" -> Json.obj(
          "name" -> Json.fromString("Alice"),
          "age" -> Json.fromInt(30),
        ),
        "tags" -> Json.arr(Json.fromString("admin"), Json.fromString("active")),
      ),
      Json.obj(
        "user" -> Json.obj(
          "name" -> Json.fromString("Alice"),
          "age" -> Json.fromInt(31),
        ),
        "tags" -> Json.arr(Json.fromString("admin"), Json.fromString("inactive")),
      ),
      s"""JsonObject(
         |  "user" -> JsonObject(
         |      "name" -> "Alice",
         |      "age" -> ${R}30$X -> ${G}31$X
         |    ),
         |  "tags" -> JArray(
         |      "admin",
         |      $R"active"$X -> $G"inactive"$X
         |    )
         |)""".stripMargin,
    )
  }

  test("Json: reports mismatch between JSON cases") {
    val result = Differ[Json].diff(Json.fromString("Alice"), Json.fromInt(1))

    assert(!result.isOk)
    result match {
      case mismatch: DiffResult.MismatchTypeResult =>
        assertEquals(mismatch.obtainedTypeName.short, "JString")
        assertEquals(mismatch.expectedTypeName.short, "JNumber")
      case other => fail(s"Expected MismatchTypeResult, got $other")
    }
  }

  test("Json: can configure nested JSON cases through OneOfDiffer") {
    val differ = Differ[Json]
      .configureRaw(ConfigurePath.of("JsonObject", "each", "JString"), ConfigureOp.ignore)
      .unsafeGet

    assertConsoleDiffOutput(
      differ,
      Json.obj("name" -> Json.fromString("Alice")),
      Json.obj("name" -> Json.fromString("Bob")),
      s"""JsonObject(
         |  "name" -> $grayIgnoredStr
         |)""".stripMargin,
    )
  }

  test("Json: can configure object values with 'each' like a map") {
    val differ = Differ[Json]
      .configureRaw(ConfigurePath.of("JsonObject", "each", "JNumber"), ConfigureOp.ignore)
      .unsafeGet

    assertConsoleDiffOutput(
      differ,
      Json.obj(
        "age" -> Json.fromInt(30),
        "name" -> Json.fromString("Alice"),
      ),
      Json.obj(
        "age" -> Json.fromInt(31),
        "name" -> Json.fromString("Bob"),
      ),
      s"""JsonObject(
         |  "age" -> $grayIgnoredStr,
         |  "name" -> $R"Alice"$X -> $G"Bob"$X
         |)""".stripMargin,
    )
  }

  test("Json: can configure array values with 'each' like a list") {
    val differ = Differ[Json]
      .configureRaw(ConfigurePath.of("JArray", "each", "JNumber"), ConfigureOp.ignore)
      .unsafeGet

    assertConsoleDiffOutput(
      differ,
      Json.arr(Json.fromInt(1), Json.fromInt(2)),
      Json.arr(Json.fromInt(3), Json.fromInt(4)),
      s"""JArray(
         |  $grayIgnoredStr,
         |  $grayIgnoredStr
         |)""".stripMargin,
    )
  }

  test("Json: can configure all JSON subtypes with typed relationships") {
    assertConsoleDiffOutput(
      Differ[Json].ignoreAt(_.subType[JNull]),
      Json.Null,
      Json.Null,
      grayIgnoredStr,
    )

    assertConsoleDiffOutput(
      Differ[Json].ignoreAt(_.subType[JBoolean]),
      Json.fromBoolean(true),
      Json.fromBoolean(false),
      grayIgnoredStr,
    )

    assertConsoleDiffOutput(
      Differ[Json].ignoreAt(_.subType[JNumber]),
      Json.fromInt(1),
      Json.fromInt(2),
      grayIgnoredStr,
    )

    assertConsoleDiffOutput(
      Differ[Json].ignoreAt(_.subType[JString]),
      Json.fromString("Alice"),
      Json.fromString("Bob"),
      grayIgnoredStr,
    )

    assertConsoleDiffOutput(
      Differ[Json].ignoreAt(_.subType[JArray]),
      Json.arr(Json.fromInt(1)),
      Json.arr(Json.fromInt(2)),
      grayIgnoredStr,
    )

    assertConsoleDiffOutput(
      Differ[Json].ignoreAt(_.subType[JsonObject]),
      Json.obj("name" -> Json.fromString("Alice")),
      Json.obj("name" -> Json.fromString("Bob")),
      grayIgnoredStr,
    )
  }

  test("Json: can configure array values with typed relationship path") {
    val differ = Differ[Json].ignoreAt(_.subType[JArray].each.subType[JNumber])

    assertConsoleDiffOutput(
      differ,
      Json.arr(Json.fromInt(1), Json.fromInt(2)),
      Json.arr(Json.fromInt(3), Json.fromInt(4)),
      s"""JArray(
         |  $grayIgnoredStr,
         |  $grayIgnoredStr
         |)""".stripMargin,
    )
  }

  test("Json: can transform a differ through typed relationship path") {
    val differ = Differ[Json].configure(_.subType[JArray].each.subType[JNumber])(_.ignore)

    assertConsoleDiffOutput(
      differ,
      Json.arr(Json.fromInt(1), Json.fromInt(2)),
      Json.arr(Json.fromInt(3), Json.fromInt(4)),
      s"""JArray(
         |  $grayIgnoredStr,
         |  $grayIgnoredStr
         |)""".stripMargin,
    )
  }

  test("Json: can configure object values with typed relationship path") {
    val differ = Differ[Json].ignoreAt(_.subType[JsonObject].each.subType[JNumber])

    assertConsoleDiffOutput(
      differ,
      Json.obj(
        "age" -> Json.fromInt(30),
        "name" -> Json.fromString("Alice"),
      ),
      Json.obj(
        "age" -> Json.fromInt(31),
        "name" -> Json.fromString("Bob"),
      ),
      s"""JsonObject(
         |  "age" -> $grayIgnoredStr,
         |  "name" -> $R"Alice"$X -> $G"Bob"$X
         |)""".stripMargin,
    )
  }

  test("JsonObject: diffs values like a map") {
    assertConsoleDiffOutput(
      Differ[JsonObject],
      JsonObject("name" -> Json.fromString("Alice")),
      JsonObject("name" -> Json.fromString("Bob")),
      s"""JsonObject(
         |  "name" -> $R"Alice"$X -> $G"Bob"$X
         |)""".stripMargin,
    )
  }

  test("JsonObject: diffs keys that only exist on one side") {
    assertConsoleDiffOutput(
      Differ[JsonObject],
      JsonObject(
        "same" -> Json.fromString("x"),
        "removed" -> Json.fromInt(1),
        "changed" -> Json.fromBoolean(true),
      ),
      JsonObject(
        "same" -> Json.fromString("x"),
        "added" -> Json.Null,
        "changed" -> Json.fromBoolean(false),
      ),
      s"""JsonObject(
         |  ${R}"removed"$X -> ${R}1$X,
         |  "same" -> "x",
         |  "changed" -> ${R}true$X -> ${G}false$X,
         |  ${G}"added"$X -> ${G}Null$X
         |)""".stripMargin,
    )
  }

  test("JsonObject: can configure values with 'each' like a map") {
    val differ = Differ[JsonObject]
      .configureRaw(ConfigurePath.of("each", "JNumber"), ConfigureOp.ignore)
      .unsafeGet

    assertConsoleDiffOutput(
      differ,
      JsonObject(
        "age" -> Json.fromInt(30),
        "name" -> Json.fromString("Alice"),
      ),
      JsonObject(
        "age" -> Json.fromInt(31),
        "name" -> Json.fromString("Bob"),
      ),
      s"""JsonObject(
         |  "age" -> $grayIgnoredStr,
         |  "name" -> $R"Alice"$X -> $G"Bob"$X
         |)""".stripMargin,
    )
  }
}

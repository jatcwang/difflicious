package difflicious.circeimport

import difflicious.*
import difflicious.circe.*
import difflicious.implicits.*
import io.circe.{Json, JsonObject}
import munit.FunSuite

class CirceImportSpec extends FunSuite {
  test("circe package wildcard import exposes Json and JsonObject differs") {
    val importedJsonDiffer: Differ[Json] = Differ[Json]
    val importedJsonObjectDiffer: Differ[JsonObject] = Differ[JsonObject]

    assert(!importedJsonDiffer.diff(Json.fromInt(1), Json.fromInt(2)).isOk)
    assert(!importedJsonObjectDiffer.diff(JsonObject("name" -> Json.fromString("Alice")), JsonObject.empty).isOk)
  }

  test("circe package wildcard import exposes Json subtype configuration helpers") {
    val configuredJsonDiffer: Differ[Json] =
      Differ[Json].ignoreAt(_.subType[JsonObject].each.subType[JNumber])

    val result = configuredJsonDiffer.diff(
      Json.obj("age" -> Json.fromInt(1)),
      Json.obj("age" -> Json.fromInt(2)),
    )

    assert(result.isOk)
  }
}

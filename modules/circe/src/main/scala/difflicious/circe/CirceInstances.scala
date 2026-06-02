package difflicious.circe

import difflicious.*
import io.circe.{Json, JsonObject}

trait CirceInstances {
  implicit val jsonDiffer: Differ[Json] = new JsonDiffer(JsonDiffer.underlyingOneOfDifferForJson)
  implicit val jsonObjectDiffer: Differ[JsonObject] = JsonDiffer.jsonObjectDiffer(jsonDiffer)
}

object CirceInstances extends CirceInstances

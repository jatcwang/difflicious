package difflicious.circe

import difflicious.*
import difflicious.utils.Eachable0
import io.circe.{Json, JsonObject}

trait CirceInstances {
  given jsonDiffer: Differ[Json] = new JsonDiffer(JsonDiffer.underlyingOneOfDifferForJson)
  given jsonObjectDiffer: Differ[JsonObject] = JsonDiffer.jsonObjectDiffer(jsonDiffer)

  given jsonNullSubTypeRelationship: DifferSubTypeRelationship[Json, JNull] =
    new DifferSubTypeRelationship("JNull")

  given jsonBooleanSubTypeRelationship: DifferSubTypeRelationship[Json, JBoolean] =
    new DifferSubTypeRelationship("JBoolean")

  given jsonNumberSubTypeRelationship: DifferSubTypeRelationship[Json, JNumber] =
    new DifferSubTypeRelationship("JNumber")

  given jsonStringSubTypeRelationship: DifferSubTypeRelationship[Json, JString] =
    new DifferSubTypeRelationship("JString")

  given jsonArraySubTypeRelationship: DifferSubTypeRelationship[Json, JArray] =
    new DifferSubTypeRelationship("JArray")

  given jsonObjectSubTypeRelationship: DifferSubTypeRelationship[Json, JsonObject] =
    new DifferSubTypeRelationship("JsonObject")

  given jsonArrayEachable: Eachable0[JArray, Json] = new Eachable0[JArray, Json] {}
  given jsonObjectEachable: Eachable0[JsonObject, Json] = new Eachable0[JsonObject, Json] {}
}

object CirceInstances extends CirceInstances

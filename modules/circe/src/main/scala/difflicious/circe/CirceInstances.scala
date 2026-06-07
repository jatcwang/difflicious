package difflicious.circe

import difflicious.*
import difflicious.utils.Eachable0
import io.circe.{Json, JsonObject}

trait CirceInstances {
  implicit val jsonDiffer: Differ[Json] = new JsonDiffer(JsonDiffer.underlyingOneOfDifferForJson)
  implicit val jsonObjectDiffer: Differ[JsonObject] = JsonDiffer.jsonObjectDiffer(jsonDiffer)

  implicit val jsonNullSubTypeRelationship: DifferSubTypeRelationship[Json, JNull] =
    new DifferSubTypeRelationship("JNull")

  implicit val jsonBooleanSubTypeRelationship: DifferSubTypeRelationship[Json, JBoolean] =
    new DifferSubTypeRelationship("JBoolean")

  implicit val jsonNumberSubTypeRelationship: DifferSubTypeRelationship[Json, JNumber] =
    new DifferSubTypeRelationship("JNumber")

  implicit val jsonStringSubTypeRelationship: DifferSubTypeRelationship[Json, JString] =
    new DifferSubTypeRelationship("JString")

  implicit val jsonArraySubTypeRelationship: DifferSubTypeRelationship[Json, JArray] =
    new DifferSubTypeRelationship("JArray")

  implicit val jsonObjectSubTypeRelationship: DifferSubTypeRelationship[Json, JsonObject] =
    new DifferSubTypeRelationship("JsonObject")

  implicit val jsonArrayEachable: Eachable0[JArray, Json] = new Eachable0[JArray, Json] {}
  implicit val jsonObjectEachable: Eachable0[JsonObject, Json] = new Eachable0[JsonObject, Json] {}
}

object CirceInstances extends CirceInstances

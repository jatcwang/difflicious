package difflicious.circe

import difflicious.{ConfigureError, ConfigureOp, ConfigurePath, DiffInput, DiffResult, Differ}
import difflicious.DiffResult.MapResult
import difflicious.differ.{LazyDiffer, MapDiffer, OneOfDiffer, SeqDiffer, ValueDiffer}
import difflicious.utils.{MapLike, SeqLike, TypeName}
import io.circe.{Json, JsonNumber, JsonObject}

import scala.collection.immutable.VectorMap

final class JsonObjectDiffer private[difflicious] (
  underlying: MapDiffer[VectorMap, String, Json],
) extends Differ[JsonObject] {
  override type R = MapResult

  override def diff(inputs: DiffInput[JsonObject]): MapResult =
    underlying.diff(inputs.map(jsonObject => VectorMap.from(jsonObject.toIterable)))

  override protected def configureIgnored(newIgnored: Boolean): JsonObjectDiffer =
    new JsonObjectDiffer(underlying.configureIgnored(newIgnored))

  override protected def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, JsonObjectDiffer] =
    underlying
      .configurePath(
        step,
        nextPath,
        op,
      )
      .map(updatedUnderlying => new JsonObjectDiffer(updatedUnderlying))

  override protected def configurePairBy(
    path: ConfigurePath,
    op: ConfigureOp.PairBy[?],
  ): Either[ConfigureError, JsonObjectDiffer] =
    underlying
      .configurePairBy(path, op)
      .map(new JsonObjectDiffer(_))
}

final class JsonDiffer private[difflicious] (underlying: OneOfDiffer[Json]) extends Differ[Json] {
  override type R = DiffResult

  override def diff(inputs: DiffInput[Json]): DiffResult =
    underlying.diff(inputs)

  override protected def configureIgnored(newIgnored: Boolean): Differ[Json] =
    new JsonDiffer(
      JsonDiffer.asOneOfDiffer(
        JsonDiffer.configureOrThrow(
          underlying.configureRaw(ConfigurePath.current, ConfigureOp.SetIgnored(newIgnored)),
        ),
      ),
    )

  override protected def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[Json]] =
    underlying
      .configureRaw(
        ConfigurePath(nextPath.resolvedSteps.dropRight(1), step :: nextPath.unresolvedSteps),
        op,
      )
      .map(updatedUnderlying => new JsonDiffer(JsonDiffer.asOneOfDiffer(updatedUnderlying)))

  override protected def configurePairBy(
    path: ConfigurePath,
    op: ConfigureOp.PairBy[?],
  ): Either[ConfigureError, Differ[Json]] =
    underlying
      .configureRaw(path, op)
      .map(updatedUnderlying => new JsonDiffer(JsonDiffer.asOneOfDiffer(updatedUnderlying)))
}

object JsonDiffer {
  private val jsonNullTypeName: TypeName[Unit] =
    TypeName(long = "io.circe.Json.JNull", short = "JNull", typeArguments = Nil)

  private val jsonBooleanTypeName: TypeName[Boolean] =
    TypeName(long = "io.circe.Json.JBoolean", short = "JBoolean", typeArguments = Nil)

  private val jsonNumberTypeName: TypeName[JsonNumber] =
    TypeName(long = "io.circe.Json.JNumber", short = "JNumber", typeArguments = Nil)

  private val jsonStringTypeName: TypeName[String] =
    TypeName(long = "io.circe.Json.JString", short = "JString", typeArguments = Nil)

  private val jsonArrayTypeName: TypeName[Vector[Json]] =
    TypeName(long = "io.circe.Json.JArray", short = "JArray", typeArguments = Nil)

  private val jsonObjectCaseTypeName: TypeName[JsonObject] =
    TypeName(long = "io.circe.Json.JObject", short = "JsonObject", typeArguments = Nil)

  private val jsonObjectTypeName: TypeName[JsonObject] =
    TypeName(long = "io.circe.JsonObject", short = "JsonObject", typeArguments = Nil)

  private val vectorSeqLike: SeqLike[Vector] = new SeqLike[Vector] {
    override def asSeq[A](f: Vector[A]): Seq[A] = f
  }

  private val vectorMapLike: MapLike[VectorMap] = new MapLike[VectorMap] {
    override def asMap[A, B](m: VectorMap[A, B]): Map[A, B] = m
  }

  private val jsonNullDiffer: ValueDiffer[Unit] =
    Differ.useEquals[Unit](_ => "Null")

  private val jsonNumberDiffer: ValueDiffer[JsonNumber] =
    Differ.useEquals[JsonNumber](_.toString)

  private def jsonObjectDiffer(valueDiffer: Differ[Json], typeName: TypeName[JsonObject]): JsonObjectDiffer =
    new JsonObjectDiffer(
      new MapDiffer[VectorMap, String, Json](
        isIgnored = false,
        keyDiffer = Differ.stringDiffer,
        valueDiffer = valueDiffer,
        typeName = typeName,
        asMap = JsonDiffer.vectorMapLike,
      ),
    )

  private[difflicious] def jsonObjectDiffer(valueDiffer: Differ[Json]): JsonObjectDiffer =
    jsonObjectDiffer(valueDiffer, JsonDiffer.jsonObjectTypeName)

  private[difflicious] lazy val underlyingOneOfDifferForJson: OneOfDiffer[Json] = {
    val recursiveDiffer = new LazyDiffer[Json](underlyingOneOfDifferForJson)

    Differ.oneOf[Json](
      OneOfDiffer.caseOf[Json, Unit](
        typeName = JsonDiffer.jsonNullTypeName,
        extract = _.asNull,
        differ = JsonDiffer.jsonNullDiffer,
      ),
      OneOfDiffer.caseOf[Json, Boolean](
        typeName = JsonDiffer.jsonBooleanTypeName,
        extract = _.asBoolean,
        differ = Differ.booleanDiffer,
      ),
      OneOfDiffer.caseOf[Json, JsonNumber](
        typeName = JsonDiffer.jsonNumberTypeName,
        extract = _.asNumber,
        differ = JsonDiffer.jsonNumberDiffer,
      ),
      OneOfDiffer.caseOf[Json, String](
        typeName = JsonDiffer.jsonStringTypeName,
        extract = _.asString,
        differ = Differ.stringDiffer,
      ),
      OneOfDiffer.caseOf[Json, Vector[Json]](
        typeName = JsonDiffer.jsonArrayTypeName,
        extract = _.asArray,
        differ = SeqDiffer.create[Vector, Json](
          itemDiffer = recursiveDiffer,
          typeName = JsonDiffer.jsonArrayTypeName,
          asSeq = JsonDiffer.vectorSeqLike,
        ),
      ),
      OneOfDiffer.caseOf[Json, JsonObject](
        typeName = JsonDiffer.jsonObjectCaseTypeName,
        extract = _.asObject,
        differ = JsonDiffer.jsonObjectDiffer(recursiveDiffer, JsonDiffer.jsonObjectCaseTypeName),
      ),
    )
  }

  private def configureOrThrow[A](configured: Either[ConfigureError, Differ[A]]): Differ[A] =
    configured match {
      case Right(differ) => differ
      case Left(error) => throw error
    }

  private def asOneOfDiffer(differ: Differ[Json]): OneOfDiffer[Json] =
    differ match {
      case oneOfDiffer: OneOfDiffer[?] => oneOfDiffer.asInstanceOf[OneOfDiffer[Json]]
      case other =>
        throw new IllegalStateException(
          s"Expected configured JsonDiffer delegate to remain OneOfDiffer, got ${other.getClass.getName}",
        )
    }
}

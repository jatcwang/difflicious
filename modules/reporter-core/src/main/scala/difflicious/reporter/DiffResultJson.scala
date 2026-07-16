package difflicious.reporter

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
import difflicious.DiffResult
import difflicious.DiffResult.ValueResult
import difflicious.PairType
import difflicious.utils.TypeName

import scala.annotation.nowarn
import scala.collection.immutable.ListMap

object DiffResultJson {
  private val EmptyJsonArray = Array('['.toByte, ']'.toByte)

  implicit lazy val diffResultJsonValueCodec: JsonValueCodec[DiffResult] = new NullCodec[DiffResult] {
    override def decodeValue(in: JsonReader, default: DiffResult): DiffResult =
      readDiffResultType(in) match {
        case "list" => listResultCodec.decodeValue(in, null)
        case "record" => recordResultCodec.decodeValue(in, null)
        case "map" => mapResultCodec.decodeValue(in, null)
        case "mismatchType" => mismatchTypeResultCodec.decodeValue(in, null)
        case "value" => valueResultCodec.decodeValue(in, null)
        case other => in.decodeError(s"Unknown DiffResult type '$other'")
      }

    override def encodeValue(value: DiffResult, out: JsonWriter): Unit =
      value match {
        case value: DiffResult.ListResult => listResultCodec.encodeValue(value, out)
        case value: DiffResult.RecordResult => recordResultCodec.encodeValue(value, out)
        case value: DiffResult.MapResult => mapResultCodec.encodeValue(value, out)
        case value: DiffResult.MismatchTypeResult => mismatchTypeResultCodec.encodeValue(value, out)
        case value: ValueResult => valueResultCodec.encodeValue(value, out)
      }
  }

  def toJsonString(result: DiffResult, config: WriterConfig = WriterConfig): String =
    writeToString(result, config)

  private implicit val typeNameCodec: JsonValueCodec[TypeName.SomeTypeName] = new NullCodec[TypeName.SomeTypeName] {
    override def decodeValue(in: JsonReader, default: TypeName.SomeTypeName): TypeName.SomeTypeName = readTypeName(in)
    override def encodeValue(value: TypeName.SomeTypeName, out: JsonWriter): Unit = writeTypeName(value, out)
  }

  private implicit val pairTypeCodec: JsonValueCodec[PairType] = new NullCodec[PairType] {
    override def decodeValue(in: JsonReader, default: PairType): PairType = readPairType(in)
    override def encodeValue(value: PairType, out: JsonWriter): Unit = out.writeVal(pairTypeString(value))
  }

  private implicit lazy val recordFieldsCodec: JsonValueCodec[ListMap[String, DiffResult]] =
    new NullCodec[ListMap[String, DiffResult]] {
      override def decodeValue(in: JsonReader, default: ListMap[String, DiffResult]): ListMap[String, DiffResult] =
        readRecordFields(in)
      override def encodeValue(value: ListMap[String, DiffResult], out: JsonWriter): Unit =
        writeRecordFields(value, out)
    }

  @nowarn("msg=match may not be exhaustive")
  private lazy val listResultCodec: JsonValueCodec[DiffResult.ListResult] =
    JsonCodecMaker.make[DiffResult.ListResult](
      CodecMakerConfig
        .withAdtLeafClassNameMapper(_ => "list")
        .withAlwaysEmitDiscriminator(true)
        .withTransientEmpty(false)
        .withRequireCollectionFields(true),
    )

  @nowarn("msg=match may not be exhaustive")
  private lazy val recordResultCodec: JsonValueCodec[DiffResult.RecordResult] =
    JsonCodecMaker.make[DiffResult.RecordResult](
      CodecMakerConfig
        .withAdtLeafClassNameMapper(_ => "record")
        .withAlwaysEmitDiscriminator(true)
        .withTransientEmpty(false)
        .withRequireCollectionFields(true),
    )

  @nowarn("msg=match may not be exhaustive")
  private lazy val mapResultCodec: JsonValueCodec[DiffResult.MapResult] =
    JsonCodecMaker.make[DiffResult.MapResult](
      CodecMakerConfig
        .withAdtLeafClassNameMapper(_ => "map")
        .withAlwaysEmitDiscriminator(true)
        .withTransientEmpty(false)
        .withRequireCollectionFields(true),
    )

  @nowarn("msg=match may not be exhaustive")
  private lazy val derivedMismatchTypeResultCodec: JsonValueCodec[DiffResult.MismatchTypeResult] =
    JsonCodecMaker.make[DiffResult.MismatchTypeResult](
      CodecMakerConfig
        .withAdtLeafClassNameMapper(_ => "mismatchType")
        .withAlwaysEmitDiscriminator(true)
        .withTransientEmpty(false)
        .withRequireCollectionFields(true),
    )

  private lazy val mismatchTypeResultCodec: JsonValueCodec[DiffResult.MismatchTypeResult] =
    new NullCodec[DiffResult.MismatchTypeResult] {
      override def decodeValue(in: JsonReader, default: DiffResult.MismatchTypeResult): DiffResult.MismatchTypeResult =
        derivedMismatchTypeResultCodec.decodeValue(in, default)

      override def encodeValue(value: DiffResult.MismatchTypeResult, out: JsonWriter): Unit =
        writeObject(out) {
          writeStringField(out, "type", "mismatchType")
          writeDiffResultField(out, "obtained", value.obtained)
          writeTypeNameField(out, "obtainedTypeName", value.obtainedTypeName)
          writeDiffResultField(out, "expected", value.expected)
          writeTypeNameField(out, "expectedTypeName", value.expectedTypeName)
          writeCommonFields(out, value)
        }
    }

  private lazy val valueResultCodec: JsonValueCodec[ValueResult] =
    new NullCodec[ValueResult] {
      override def decodeValue(in: JsonReader, default: ValueResult): ValueResult = readValueResult(in)
      override def encodeValue(value: ValueResult, out: JsonWriter): Unit = writeValueResult(value, out)
    }

  private abstract class NullCodec[A <: AnyRef] extends JsonValueCodec[A] {
    override def nullValue: A = null.asInstanceOf[A]
  }

  private def readDiffResultType(in: JsonReader): String = {
    in.setMark()
    expect(in, '{')

    var resultType: String = null
    var continue = !in.isNextToken('}'.toByte)
    if (continue) in.rollbackToken()
    while (continue && resultType == null) {
      in.readKeyAsString() match {
        case "type" => resultType = in.readString(null)
        case _ => in.skip()
      }
      continue = in.isNextToken(','.toByte)
    }

    in.rollbackToMark()
    if (resultType == null) in.requiredFieldError("type")
    resultType
  }

  private def readTypeName(in: JsonReader): TypeName.SomeTypeName = {
    expect(in, '{')

    var short: String = null
    var long: String = null
    var typeArguments: List[TypeName.SomeTypeName] = null

    readFields(in) {
      case "short" => short = in.readString(null)
      case "long" => long = in.readString(null)
      case "typeArguments" => typeArguments = readArray(in)(readTypeName).toList
      case _ => in.skip()
    }

    requireField(in, short, "short")
    requireField(in, long, "long")
    requireField(in, typeArguments, "typeArguments")
    TypeName[Any](long = long, short = short, typeArguments = typeArguments)
  }

  private def readRecordFields(in: JsonReader): ListMap[String, DiffResult] = {
    val builder = ListMap.newBuilder[String, DiffResult]
    readArrayValues(in) { in =>
      expect(in, '{')
      var name: String = null
      var value: DiffResult = null

      readFields(in) {
        case "name" => name = in.readString(null)
        case "value" => value = diffResultJsonValueCodec.decodeValue(in, null)
        case _ => in.skip()
      }

      requireField(in, name, "name")
      requireField(in, value, "value")
      builder += name -> value
    }
    builder.result()
  }

  private def readValueResult(in: JsonReader): ValueResult = {
    expect(in, '{')

    var resultType: String = null
    var valueType: String = null
    var obtained: String = null
    var expected: String = null
    var isSame = false
    var hasIsSame = false
    var isIgnored = false
    var hasIsIgnored = false

    readFields(in) {
      case "type" => resultType = in.readString(null)
      case "valueType" => valueType = in.readString(null)
      case "obtained" => obtained = in.readString(null)
      case "expected" => expected = in.readString(null)
      case "isSame" =>
        isSame = in.readBoolean()
        hasIsSame = true
      case "isIgnored" =>
        isIgnored = in.readBoolean()
        hasIsIgnored = true
      case _ => in.skip()
    }

    requireField(in, resultType, "type")
    if (resultType != "value") in.decodeError(s"Expected DiffResult type 'value' but found '$resultType'")
    requireField(in, valueType, "valueType")
    requireField(in, hasIsIgnored, "isIgnored")

    valueType match {
      case "both" =>
        requireField(in, obtained, "obtained")
        requireField(in, expected, "expected")
        requireField(in, hasIsSame, "isSame")
        ValueResult.Both(obtained, expected, isSame, isIgnored)
      case "obtainedOnly" =>
        requireField(in, obtained, "obtained")
        ValueResult.ObtainedOnly(obtained, isIgnored)
      case "expectedOnly" =>
        requireField(in, expected, "expected")
        ValueResult.ExpectedOnly(expected, isIgnored)
      case other => in.decodeError(s"Unknown DiffResult valueType '$other'")
    }
  }

  private def readPairType(in: JsonReader): PairType =
    in.readString(null) match {
      case "both" => PairType.Both
      case "obtainedOnly" => PairType.ObtainedOnly
      case "expectedOnly" => PairType.ExpectedOnly
      case other => in.decodeError(s"Unknown pairType '$other'")
    }

  private def readFields(in: JsonReader)(readField: String => Unit): Unit =
    if (!in.isNextToken('}'.toByte)) {
      in.rollbackToken()
      var continue = true
      while (continue) {
        readField(in.readKeyAsString())
        continue = in.isNextToken(','.toByte)
      }
      if (!in.isCurrentToken('}'.toByte)) in.decodeError("expected ',' or '}'")
    }

  private def readArray[A](in: JsonReader)(readValue: JsonReader => A): Vector[A] = {
    val builder = Vector.newBuilder[A]
    readArrayValues(in) { in =>
      builder += readValue(in)
      ()
    }
    builder.result()
  }

  private def readArrayValues(in: JsonReader)(readValue: JsonReader => Unit): Unit = {
    expect(in, '[')

    if (!in.isNextToken(']'.toByte)) {
      in.rollbackToken()
      var continue = true
      while (continue) {
        readValue(in)
        continue = in.isNextToken(','.toByte)
      }
      if (!in.isCurrentToken(']'.toByte)) in.decodeError("expected ',' or ']'")
    }
  }

  private def expect(in: JsonReader, token: Char): Unit =
    if (!in.isNextToken(token.toByte)) in.decodeError(s"expected '$token'")

  private def requireField[A](in: JsonReader, value: A, name: String): Unit =
    if (value == null) in.requiredFieldError(name)

  private def requireField(in: JsonReader, seen: Boolean, name: String): Unit =
    if (!seen) in.requiredFieldError(name)

  private def writeTypeNameField(out: JsonWriter, fieldName: String, typeName: TypeName.SomeTypeName): Unit = {
    out.writeKey(fieldName)
    writeTypeName(typeName, out)
  }

  private def writeTypeName(typeName: TypeName.SomeTypeName, out: JsonWriter): Unit =
    writeObject(out) {
      writeStringField(out, "short", typeName.short)
      writeStringField(out, "long", typeName.long)
      writeArrayField(out, "typeArguments", typeName.typeArguments)(writeTypeName)
    }

  private def writeRecordFields(fields: ListMap[String, DiffResult], out: JsonWriter): Unit =
    writeArray(out, fields) { case ((name, value), out) =>
      writeObject(out) {
        writeStringField(out, "name", name)
        writeDiffResultField(out, "value", value)
      }
    }

  private def writeValueResult(result: ValueResult, out: JsonWriter): Unit =
    result match {
      case result: ValueResult.Both =>
        writeObject(out) {
          writeStringField(out, "type", "value")
          writeStringField(out, "valueType", "both")
          writeStringField(out, "obtained", result.obtained)
          writeStringField(out, "expected", result.expected)
          writeBooleanField(out, "isSame", result.isSame)
          writeCommonFields(out, result)
        }
      case result: ValueResult.ObtainedOnly =>
        writeObject(out) {
          writeStringField(out, "type", "value")
          writeStringField(out, "valueType", "obtainedOnly")
          writeStringField(out, "obtained", result.obtained)
          writeCommonFields(out, result)
        }
      case result: ValueResult.ExpectedOnly =>
        writeObject(out) {
          writeStringField(out, "type", "value")
          writeStringField(out, "valueType", "expectedOnly")
          writeStringField(out, "expected", result.expected)
          writeCommonFields(out, result)
        }
    }

  private def writeCommonFields(out: JsonWriter, result: DiffResult): Unit = {
    writeStringField(out, "pairType", pairTypeString(result.pairType))
    writeBooleanField(out, "isIgnored", result.isIgnored)
    writeBooleanField(out, "isOk", result.isOk)
  }

  private def writeDiffResultField(out: JsonWriter, fieldName: String, result: DiffResult): Unit = {
    out.writeKey(fieldName)
    diffResultJsonValueCodec.encodeValue(result, out)
  }

  private def writeStringField(out: JsonWriter, fieldName: String, value: String): Unit = {
    out.writeKey(fieldName)
    out.writeVal(value)
  }

  private def writeBooleanField(out: JsonWriter, fieldName: String, value: Boolean): Unit = {
    out.writeKey(fieldName)
    out.writeVal(value)
  }

  private def writeArrayField[A](out: JsonWriter, fieldName: String, values: Iterable[A])(
    writeValue: (A, JsonWriter) => Unit,
  ): Unit = {
    out.writeKey(fieldName)
    writeArray(out, values)(writeValue)
  }

  private def writeArray[A](out: JsonWriter, values: Iterable[A])(writeValue: (A, JsonWriter) => Unit): Unit = {
    if (values.isEmpty) out.writeRawVal(EmptyJsonArray)
    else {
      out.writeArrayStart()
      values.foreach(writeValue(_, out))
      out.writeArrayEnd()
    }
  }

  private def writeObject(out: JsonWriter)(writeFields: => Unit): Unit = {
    out.writeObjectStart()
    writeFields
    out.writeObjectEnd()
  }

  private def pairTypeString(pairType: PairType): String =
    pairType match {
      case PairType.Both => "both"
      case PairType.ObtainedOnly => "obtainedOnly"
      case PairType.ExpectedOnly => "expectedOnly"
    }
}

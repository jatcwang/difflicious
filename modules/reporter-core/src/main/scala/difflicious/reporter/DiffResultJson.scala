package difflicious.reporter

import com.github.plokhotnyuk.jsoniter_scala.core.*
import difflicious.DiffResult
import difflicious.DiffResult.MapResult
import difflicious.DiffResult.ValueResult
import difflicious.PairType
import difflicious.utils.TypeName

import scala.collection.immutable.ListMap

object DiffResultJson {
  private val EmptyJsonArray = Array('['.toByte, ']'.toByte)

  implicit lazy val diffResultJsonValueCodec: JsonValueCodec[DiffResult] = new NullCodec[DiffResult] {
    override def decodeValue(in: JsonReader, default: DiffResult): DiffResult =
      readDiffResultType(in) match {
        case "list" => readListResult(in)
        case "record" => readRecordResult(in)
        case "map" => readMapResult(in)
        case "mismatchType" => readMismatchTypeResult(in)
        case "value" => readValueResult(in)
        case other => in.decodeError(s"Unknown DiffResult type '$other'")
      }

    override def encodeValue(value: DiffResult, out: JsonWriter): Unit =
      value match {
        case value: DiffResult.ListResult => writeListResult(value, out)
        case value: DiffResult.RecordResult => writeRecordResult(value, out)
        case value: DiffResult.MapResult => writeMapResult(value, out)
        case value: DiffResult.MismatchTypeResult => writeMismatchTypeResult(value, out)
        case value: ValueResult => writeValueResult(value, out)
      }
  }

  def toJsonString(result: DiffResult, config: WriterConfig = WriterConfig): String =
    writeToString(result, config)

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

  private def readListResult(in: JsonReader): DiffResult.ListResult = {
    expect(in, '{')

    var resultType: String = null
    var typeName: TypeName.SomeTypeName = null
    var items: Vector[DiffResult] = null
    var pairType: PairType = null
    var isIgnored = false
    var hasIsIgnored = false
    var isOk = false
    var hasIsOk = false

    readFields(in) {
      case "type" => resultType = in.readString(null)
      case "typeName" => typeName = readTypeName(in)
      case "items" => items = readArray(in)(diffResultJsonValueCodec.decodeValue(_, null))
      case "pairType" => pairType = readPairType(in)
      case "isIgnored" =>
        isIgnored = in.readBoolean()
        hasIsIgnored = true
      case "isOk" =>
        isOk = in.readBoolean()
        hasIsOk = true
      case _ => in.skip()
    }

    requireResultType(in, resultType, "list")
    requireField(in, typeName, "typeName")
    requireField(in, items, "items")
    requireField(in, pairType, "pairType")
    requireField(in, hasIsIgnored, "isIgnored")
    requireField(in, hasIsOk, "isOk")
    DiffResult.ListResult(typeName, items, pairType, isIgnored, isOk)
  }

  private def readRecordResult(in: JsonReader): DiffResult.RecordResult = {
    expect(in, '{')

    var resultType: String = null
    var typeName: TypeName.SomeTypeName = null
    var fields: ListMap[String, DiffResult] = null
    var pairType: PairType = null
    var isIgnored = false
    var hasIsIgnored = false
    var isOk = false
    var hasIsOk = false

    readFields(in) {
      case "type" => resultType = in.readString(null)
      case "typeName" => typeName = readTypeName(in)
      case "fields" => fields = readRecordFields(in)
      case "pairType" => pairType = readPairType(in)
      case "isIgnored" =>
        isIgnored = in.readBoolean()
        hasIsIgnored = true
      case "isOk" =>
        isOk = in.readBoolean()
        hasIsOk = true
      case _ => in.skip()
    }

    requireResultType(in, resultType, "record")
    requireField(in, typeName, "typeName")
    requireField(in, fields, "fields")
    requireField(in, pairType, "pairType")
    requireField(in, hasIsIgnored, "isIgnored")
    requireField(in, hasIsOk, "isOk")
    DiffResult.RecordResult(typeName, fields, pairType, isIgnored, isOk)
  }

  private def readMapResult(in: JsonReader): DiffResult.MapResult = {
    expect(in, '{')

    var resultType: String = null
    var typeName: TypeName.SomeTypeName = null
    var entries: Vector[MapResult.Entry] = null
    var pairType: PairType = null
    var isIgnored = false
    var hasIsIgnored = false
    var isOk = false
    var hasIsOk = false

    readFields(in) {
      case "type" => resultType = in.readString(null)
      case "typeName" => typeName = readTypeName(in)
      case "entries" => entries = readArray(in)(readMapEntry)
      case "pairType" => pairType = readPairType(in)
      case "isIgnored" =>
        isIgnored = in.readBoolean()
        hasIsIgnored = true
      case "isOk" =>
        isOk = in.readBoolean()
        hasIsOk = true
      case _ => in.skip()
    }

    requireResultType(in, resultType, "map")
    requireField(in, typeName, "typeName")
    requireField(in, entries, "entries")
    requireField(in, pairType, "pairType")
    requireField(in, hasIsIgnored, "isIgnored")
    requireField(in, hasIsOk, "isOk")
    DiffResult.MapResult(typeName, entries, pairType, isIgnored, isOk)
  }

  private def readMapEntry(in: JsonReader): MapResult.Entry = {
    expect(in, '{')
    var key: String = null
    var value: DiffResult = null

    readFields(in) {
      case "key" => key = in.readString(null)
      case "value" => value = diffResultJsonValueCodec.decodeValue(in, null)
      case _ => in.skip()
    }

    requireField(in, key, "key")
    requireField(in, value, "value")
    MapResult.Entry(key, value)
  }

  private def readMismatchTypeResult(in: JsonReader): DiffResult.MismatchTypeResult = {
    expect(in, '{')

    var resultType: String = null
    var obtained: DiffResult = null
    var obtainedTypeName: TypeName.SomeTypeName = null
    var expected: DiffResult = null
    var expectedTypeName: TypeName.SomeTypeName = null
    var pairType: PairType = null
    var isIgnored = false
    var hasIsIgnored = false

    readFields(in) {
      case "type" => resultType = in.readString(null)
      case "obtained" => obtained = diffResultJsonValueCodec.decodeValue(in, null)
      case "obtainedTypeName" => obtainedTypeName = readTypeName(in)
      case "expected" => expected = diffResultJsonValueCodec.decodeValue(in, null)
      case "expectedTypeName" => expectedTypeName = readTypeName(in)
      case "pairType" => pairType = readPairType(in)
      case "isIgnored" =>
        isIgnored = in.readBoolean()
        hasIsIgnored = true
      case _ => in.skip()
    }

    requireResultType(in, resultType, "mismatchType")
    requireField(in, obtained, "obtained")
    requireField(in, obtainedTypeName, "obtainedTypeName")
    requireField(in, expected, "expected")
    requireField(in, expectedTypeName, "expectedTypeName")
    requireField(in, pairType, "pairType")
    requireField(in, hasIsIgnored, "isIgnored")
    DiffResult.MismatchTypeResult(obtained, obtainedTypeName, expected, expectedTypeName, pairType, isIgnored)
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
    var typeName: TypeName.SomeTypeName = null
    var obtained: String = null
    var expected: String = null
    var isSame = false
    var hasIsSame = false
    var isIgnored = false
    var hasIsIgnored = false

    readFields(in) {
      case "type" => resultType = in.readString(null)
      case "valueType" => valueType = in.readString(null)
      case "typeName" => typeName = readTypeName(in)
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
    requireField(in, typeName, "typeName")
    requireField(in, hasIsIgnored, "isIgnored")

    valueType match {
      case "both" =>
        requireField(in, obtained, "obtained")
        requireField(in, expected, "expected")
        requireField(in, hasIsSame, "isSame")
        ValueResult.Both(typeName, obtained, expected, isSame, isIgnored)
      case "obtainedOnly" =>
        requireField(in, obtained, "obtained")
        ValueResult.ObtainedOnly(typeName, obtained, isIgnored)
      case "expectedOnly" =>
        requireField(in, expected, "expected")
        ValueResult.ExpectedOnly(typeName, expected, isIgnored)
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

  private def requireResultType(in: JsonReader, actual: String, expected: String): Unit = {
    requireField(in, actual, "type")
    if (actual != expected) in.decodeError(s"Expected DiffResult type '$expected' but found '$actual'")
  }

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

  private def writeListResult(result: DiffResult.ListResult, out: JsonWriter): Unit =
    writeObject(out) {
      writeStringField(out, "type", "list")
      writeTypeNameField(out, "typeName", result.typeName)
      writeArrayField(out, "items", result.items)((value, out) => diffResultJsonValueCodec.encodeValue(value, out))
      writeCommonFields(out, result)
    }

  private def writeRecordResult(result: DiffResult.RecordResult, out: JsonWriter): Unit =
    writeObject(out) {
      writeStringField(out, "type", "record")
      writeTypeNameField(out, "typeName", result.typeName)
      out.writeKey("fields")
      writeRecordFields(result.fields, out)
      writeCommonFields(out, result)
    }

  private def writeMapResult(result: DiffResult.MapResult, out: JsonWriter): Unit =
    writeObject(out) {
      writeStringField(out, "type", "map")
      writeTypeNameField(out, "typeName", result.typeName)
      writeArrayField(out, "entries", result.entries) { (entry, out) =>
        writeObject(out) {
          writeStringField(out, "key", entry.key)
          writeDiffResultField(out, "value", entry.value)
        }
      }
      writeCommonFields(out, result)
    }

  private def writeRecordFields(fields: ListMap[String, DiffResult], out: JsonWriter): Unit =
    writeArray(out, fields) { case ((name, value), out) =>
      writeObject(out) {
        writeStringField(out, "name", name)
        writeDiffResultField(out, "value", value)
      }
    }

  private def writeMismatchTypeResult(result: DiffResult.MismatchTypeResult, out: JsonWriter): Unit =
    writeObject(out) {
      writeStringField(out, "type", "mismatchType")
      writeDiffResultField(out, "obtained", result.obtained)
      writeTypeNameField(out, "obtainedTypeName", result.obtainedTypeName)
      writeDiffResultField(out, "expected", result.expected)
      writeTypeNameField(out, "expectedTypeName", result.expectedTypeName)
      writeCommonFields(out, result)
    }

  private def writeValueResult(result: ValueResult, out: JsonWriter): Unit =
    result match {
      case result: ValueResult.Both =>
        writeObject(out) {
          writeStringField(out, "type", "value")
          writeStringField(out, "valueType", "both")
          writeTypeNameField(out, "typeName", result.typeName)
          writeStringField(out, "obtained", result.obtained)
          writeStringField(out, "expected", result.expected)
          writeBooleanField(out, "isSame", result.isSame)
          writeCommonFields(out, result)
        }
      case result: ValueResult.ObtainedOnly =>
        writeObject(out) {
          writeStringField(out, "type", "value")
          writeStringField(out, "valueType", "obtainedOnly")
          writeTypeNameField(out, "typeName", result.typeName)
          writeStringField(out, "obtained", result.obtained)
          writeCommonFields(out, result)
        }
      case result: ValueResult.ExpectedOnly =>
        writeObject(out) {
          writeStringField(out, "type", "value")
          writeStringField(out, "valueType", "expectedOnly")
          writeTypeNameField(out, "typeName", result.typeName)
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

package difflicious.reporter

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString}
import difflicious.DiffResult
import difflicious.DiffResult.MapResult
import difflicious.DiffResult.ValueResult
import difflicious.PairType
import difflicious.utils.TypeName
import io.circe.Json
import io.circe.parser.parse
import munit.FunSuite

import scala.collection.immutable.ListMap

class DiffResultJsonSpec extends FunSuite {
  private implicit val diffResultCodec: JsonValueCodec[DiffResult] =
    DiffResultJson.diffResultJsonValueCodec

  private val intTypeName = TypeName[Int]

  private val listTypeName = TypeName[List[Int]](
    long = "scala.collection.immutable.List",
    short = "List",
    typeArguments = List(intTypeName),
  )

  private val personTypeName = TypeName[Any](
    long = "example.Person",
    short = "Person",
    typeArguments = Nil,
  )

  test("writes and reads ValueResult.Both") {
    val result: DiffResult =
      ValueResult.Both(intTypeName, "1", "2", isSame = false, isIgnored = false)

    assertWritesAndReads(
      result,
      parseJson(
        """{
          |  "type": "value",
          |  "valueType": "both",
          |  "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
          |  "obtained": "1",
          |  "expected": "2",
          |  "isSame": false,
          |  "pairType": "both",
          |  "isIgnored": false,
          |  "isOk": false
          |}""".stripMargin,
      ),
    )
  }

  test("writes and reads ValueResult.ObtainedOnly") {
    val result: DiffResult = ValueResult.ObtainedOnly(intTypeName, "extra", isIgnored = false)

    assertWritesAndReads(
      result,
      parseJson(
        """{
          |  "type": "value",
          |  "valueType": "obtainedOnly",
          |  "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
          |  "obtained": "extra",
          |  "pairType": "obtainedOnly",
          |  "isIgnored": false,
          |  "isOk": false
          |}""".stripMargin,
      ),
    )
  }

  test("writes and reads ValueResult.ExpectedOnly") {
    val result: DiffResult = ValueResult.ExpectedOnly(intTypeName, "missing", isIgnored = false)

    assertWritesAndReads(
      result,
      parseJson(
        """{
          |  "type": "value",
          |  "valueType": "expectedOnly",
          |  "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
          |  "expected": "missing",
          |  "pairType": "expectedOnly",
          |  "isIgnored": false,
          |  "isOk": false
          |}""".stripMargin,
      ),
    )
  }

  test("writes and reads ListResult") {
    val result = DiffResult.ListResult(
      typeName = listTypeName,
      items = Vector(
        ValueResult.Both(intTypeName, "1", "1", isSame = true, isIgnored = false),
        ValueResult.Both(intTypeName, "2", "3", isSame = false, isIgnored = false),
      ),
      pairType = PairType.Both,
      isIgnored = false,
      isOk = false,
    )

    assertWritesAndReads(
      result,
      parseJson("""{
        |  "type": "list",
        |  "typeName": {
        |    "short": "List",
        |    "long": "scala.collection.immutable.List",
        |    "typeArguments": [
        |      {
        |        "short": "Int",
        |        "long": "scala.Int",
        |        "typeArguments": []
        |      }
        |    ]
        |  },
        |  "items": [
        |    {
        |      "type": "value",
        |      "valueType": "both",
        |      "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
        |      "obtained": "1",
        |      "expected": "1",
        |      "isSame": true,
        |      "pairType": "both",
        |      "isIgnored": false,
        |      "isOk": true
        |    },
        |    {
        |      "type": "value",
        |      "valueType": "both",
        |      "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
        |      "obtained": "2",
        |      "expected": "3",
        |      "isSame": false,
        |      "pairType": "both",
        |      "isIgnored": false,
        |      "isOk": false
        |    }
        |  ],
        |  "pairType": "both",
        |  "isIgnored": false,
        |  "isOk": false
        |}""".stripMargin),
    )
  }

  test("writes and reads RecordResult") {
    val result = DiffResult.RecordResult(
      typeName = personTypeName,
      fields = ListMap(
        "name" -> ValueResult.Both(intTypeName, "Alice", "Bob", isSame = false, isIgnored = false),
        "age" -> ValueResult.Both(intTypeName, "30", "30", isSame = true, isIgnored = false),
      ),
      pairType = PairType.Both,
      isIgnored = false,
      isOk = false,
    )

    assertWritesAndReads(
      result,
      parseJson("""{
        |  "type": "record",
        |  "typeName": {
        |    "short": "Person",
        |    "long": "example.Person",
        |    "typeArguments": []
        |  },
        |  "fields": [
        |    {
        |      "name": "name",
        |      "value": {
        |        "type": "value",
        |        "valueType": "both",
        |        "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
        |        "obtained": "Alice",
        |        "expected": "Bob",
        |        "isSame": false,
        |        "pairType": "both",
        |        "isIgnored": false,
        |        "isOk": false
        |      }
        |    },
        |    {
        |      "name": "age",
        |      "value": {
        |        "type": "value",
        |        "valueType": "both",
        |        "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
        |        "obtained": "30",
        |        "expected": "30",
        |        "isSame": true,
        |        "pairType": "both",
        |        "isIgnored": false,
        |        "isOk": true
        |      }
        |    }
        |  ],
        |  "pairType": "both",
        |  "isIgnored": false,
        |  "isOk": false
        |}""".stripMargin),
    )
  }

  test("writes and reads MapResult") {
    val result = DiffResult.MapResult(
      typeName = TypeName[Map[String, Int]](
        long = "scala.collection.immutable.Map",
        short = "Map",
        typeArguments =
          List(TypeName[String](long = "java.lang.String", short = "String", typeArguments = Nil), intTypeName),
      ),
      entries = Vector(
        MapResult.Entry(
          key = "a",
          value = ValueResult.Both(intTypeName, "1", "1", isSame = true, isIgnored = false),
        ),
        MapResult.Entry(
          key = "b",
          value = ValueResult.ExpectedOnly(intTypeName, "2", isIgnored = false),
        ),
      ),
      pairType = PairType.Both,
      isIgnored = false,
      isOk = false,
    )

    assertWritesAndReads(
      result,
      parseJson("""{
        |  "type": "map",
        |  "typeName": {
        |    "short": "Map",
        |    "long": "scala.collection.immutable.Map",
        |    "typeArguments": [
        |      {
        |        "short": "String",
        |        "long": "java.lang.String",
        |        "typeArguments": []
        |      },
        |      {
        |        "short": "Int",
        |        "long": "scala.Int",
        |        "typeArguments": []
        |      }
        |    ]
        |  },
        |  "entries": [
        |    {
        |      "key": "a",
        |      "value": {
        |        "type": "value",
        |        "valueType": "both",
        |        "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
        |        "obtained": "1",
        |        "expected": "1",
        |        "isSame": true,
        |        "pairType": "both",
        |        "isIgnored": false,
        |        "isOk": true
        |      }
        |    },
        |    {
        |      "key": "b",
        |      "value": {
        |        "type": "value",
        |        "valueType": "expectedOnly",
        |        "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
        |        "expected": "2",
        |        "pairType": "expectedOnly",
        |        "isIgnored": false,
        |        "isOk": false
        |      }
        |    }
        |  ],
        |  "pairType": "both",
        |  "isIgnored": false,
        |  "isOk": false
        |}""".stripMargin),
    )
  }

  test("writes and reads MismatchTypeResult") {
    val result = DiffResult.MismatchTypeResult(
      obtained = ValueResult.ObtainedOnly(intTypeName, "1", isIgnored = false),
      obtainedTypeName = intTypeName,
      expected = ValueResult.ExpectedOnly(intTypeName, "true", isIgnored = false),
      expectedTypeName = TypeName[Boolean](long = "scala.Boolean", short = "Boolean", typeArguments = Nil),
      pairType = PairType.Both,
      isIgnored = false,
    )

    assertWritesAndReads(
      result,
      parseJson("""{
        |  "type": "mismatchType",
        |  "obtained": {
        |    "type": "value",
        |    "valueType": "obtainedOnly",
        |    "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
        |    "obtained": "1",
        |    "pairType": "obtainedOnly",
        |    "isIgnored": false,
        |    "isOk": false
        |  },
        |  "obtainedTypeName": {
        |    "short": "Int",
        |    "long": "scala.Int",
        |    "typeArguments": []
        |  },
        |  "expected": {
        |    "type": "value",
        |    "valueType": "expectedOnly",
        |    "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
        |    "expected": "true",
        |    "pairType": "expectedOnly",
        |    "isIgnored": false,
        |    "isOk": false
        |  },
        |  "expectedTypeName": {
        |    "short": "Boolean",
        |    "long": "scala.Boolean",
        |    "typeArguments": []
        |  },
        |  "pairType": "both",
        |  "isIgnored": false,
        |  "isOk": false
        |}""".stripMargin),
    )
  }

  test("decodes JSONL records with ids") {
    val json =
      """{
        |  "runId": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
        |  "testId": "01ARZ3NDEKTSV4RRFFQ69G5FAW",
        |  "suiteName": "ExampleSuite",
        |  "suiteId": "example.ExampleSuite",
        |  "suiteClassName": null,
        |  "testName": "compares values",
        |  "testText": "compares values",
        |  "testHierarchy": [
        |    "compares values"
        |  ],
        |  "fileName": "ExampleSuite.scala",
        |  "filePath": "/workspace/ExampleSuite.scala",
        |  "lineNumber": 37,
        |  "diffResult": {
        |    "type": "value",
        |    "valueType": "both",
        |    "typeName": {"short": "Int", "long": "scala.Int", "typeArguments": []},
        |    "obtained": "1",
        |    "expected": "2",
        |    "isSame": false,
        |    "pairType": "both",
        |    "isIgnored": false,
        |    "isOk": false
        |  }
        |}""".stripMargin

    val record = DiffResultTestDetails.fromJsonString(json)

    assertEquals(record.runId, "01ARZ3NDEKTSV4RRFFQ69G5FAV")
    assertEquals(record.testId, "01ARZ3NDEKTSV4RRFFQ69G5FAW")
    assertEquals(
      record.diffResult,
      ValueResult.Both(intTypeName, "1", "2", isSame = false, isIgnored = false),
    )
  }

  private def assertWritesAndReads(result: DiffResult, expectedJson: Json): Unit = {
    val actualJson = DiffResultJson.toJsonString(result)

    assertEquals(parseJson(actualJson), expectedJson)
    assertEquals(readFromString[DiffResult](actualJson), result)
  }

  private def parseJson(value: String): Json =
    parse(value).fold(error => fail(s"Invalid JSON: ${error.message}"), identity)
}

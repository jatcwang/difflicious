package difflicious

import difflicious.utils.TypeName

class TypeNameSpec extends munit.FunSuite {
  test("TypeName can be derived implicitly for nested applied types") {
    val typeName = implicitly[TypeName[Map[String, List[Int]]]]

    assertEquals(
      typeName.withTypeParamsLong,
      "scala.collection.immutable.Map[java.lang.String,scala.collection.immutable.List[scala.Int]]",
    )
  }

  test("TypeName macro can be called directly") {
    val typeName = TypeName[Option[String]]

    assertEquals(typeName.withTypeParamsLong, "scala.Option[java.lang.String]")
  }

  test("TypeName normalizes types owned by objects") {
    val typeName = TypeName[testtypes.CC]

    assertEquals(typeName.withTypeParamsLong, "difflicious.testtypes.CC")
  }
}

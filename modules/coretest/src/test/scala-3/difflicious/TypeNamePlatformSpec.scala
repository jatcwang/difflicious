package difflicious

import difflicious.utils.TypeName

object OpaqueTypeNames {
  opaque type OpaqueTypeName = Int

  val typeName: TypeName[OpaqueTypeName] = TypeName[OpaqueTypeName]
}

class TypeNamePlatformSpec extends munit.FunSuite {
  test("TypeName preserves an opaque type name within its defining scope") {
    assertEquals(OpaqueTypeNames.typeName.withTypeParamsLong, "difflicious.OpaqueTypeNames.OpaqueTypeName")
  }

  test("TypeName preserves an opaque type name outside its defining scope") {
    assertEquals(
      TypeName[OpaqueTypeNames.OpaqueTypeName].withTypeParamsLong,
      "difflicious.OpaqueTypeNames.OpaqueTypeName",
    )
  }
}

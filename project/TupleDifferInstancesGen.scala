object TupleDifferInstancesGen {
  val fileContent: String = {
    val iter = (2 to 22)
      .map { tupleSize =>
        val typeList = (1 to tupleSize).map(t => s"A$t").mkString(", ")
        val tupleName = s"Tuple$tupleSize"
        val typeName = s"$tupleName[${typeList}]"

        s"""
         |implicit def tuple$tupleSize[$typeList](
         |  implicit ${(1 to tupleSize)
            .map { t =>
              s"a${t}Diff: Differ[A$t]"
            }
            .mkString(",\n  ")},
         |  typeName: TypeName[${typeName}]
         |): ProductDiffer[$typeName] = new ProductDiffer[$typeName](
         |  Vector(
         |    ${(1 to tupleSize)
            .map { t =>
              s""""_$t" -> a${t}Diff.asInstanceOf[Differ[Any]]"""
            }
            .mkString(",\n    ")}
         |  ),
         |  isIgnored = false,
         |  typeName = typeName,
         |  canUseEqualsValue = ${(1 to tupleSize).map(t => s"a${t}Diff.canUseEquals").mkString(" && ")},
         |)
         |""".stripMargin
      }
      .mkString("\n")
      .linesIterator
      .map(s => s"  $s")
      .mkString("\n")

    s"""
      |package difflicious
      |
      |import difflicious.differ.ProductDiffer
      |import difflicious.utils.TypeName
      |
      |// $$COVERAGE-OFF$$
      |trait DifferTupleInstances {
      |  ${iter}
      |}
      |// $$COVERAGE-ON$$
      |""".stripMargin

  }
}

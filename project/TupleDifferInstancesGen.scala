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
         |): RecordDiffer[$typeName] = new RecordDiffer[$typeName](
         |  ListMap(
         |    ${(1 to tupleSize)
             .map { t =>
               s""""_$t" -> Tuple2(_._$t, a${t}Diff.asInstanceOf[Differ[Any]])"""
             }
             .mkString(",\n    ")}
         |  ),
         |  isIgnored = false,
         |  typeName = typeName,
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
      |import difflicious.differ.RecordDiffer
      |import difflicious.utils.TypeName
      |
      |import scala.collection.immutable.ListMap
      |// $$COVERAGE-OFF$$
      |trait DifferTupleInstances {
      |  ${iter}
      |}
      |// $$COVERAGE-ON$$
      |""".stripMargin


  }
}

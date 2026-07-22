{
  val DifferGen_this: difflicious.Differ.type = difflicious.Differ

  ({
    lazy val derivedDiffer$macro$3: difflicious.Differ[difflicious.testtypes.TreeContainer] =
      (new difflicious.differ.LazyDiffer[difflicious.testtypes.TreeContainer]({
        val fieldDiffers: Vector[Tuple2[String, difflicious.Differ[Any]]] = scala.Vector
          .empty[Tuple2[String, difflicious.Differ[Any]]]
          .:+[Tuple2[String, difflicious.Differ[Any]]](
            Tuple2
              .apply[String, difflicious.Differ[Any]]("c1", derivedDiffer$macro$4.asInstanceOf[difflicious.Differ[Any]]),
          )
          .:+[Tuple2[String, difflicious.Differ[Any]]](
            Tuple2
              .apply[String, difflicious.Differ[Any]]("c2", derivedDiffer$macro$6.asInstanceOf[difflicious.Differ[Any]]),
          )
          .:+[Tuple2[String, difflicious.Differ[Any]]](
            Tuple2.apply[String, difflicious.Differ[Any]](
              "option",
              derivedDiffer$macro$8.asInstanceOf[difflicious.Differ[Any]],
            ),
          )
          .:+[Tuple2[String, difflicious.Differ[Any]]](
            Tuple2.apply[String, difflicious.Differ[Any]](
              "either",
              derivedDiffer$macro$9.asInstanceOf[difflicious.Differ[Any]],
            ),
          )
          .:+[Tuple2[String, difflicious.Differ[Any]]](
            Tuple2.apply[String, difflicious.Differ[Any]](
              "map",
              derivedDiffer$macro$10.asInstanceOf[difflicious.Differ[Any]],
            ),
          )
          .:+[Tuple2[String, difflicious.Differ[Any]]](
            Tuple2.apply[String, difflicious.Differ[Any]](
              "list",
              derivedDiffer$macro$11.asInstanceOf[difflicious.Differ[Any]],
            ),
          )

        (new difflicious.differ.ProductDiffer[difflicious.testtypes.TreeContainer](
          fieldDiffers = fieldDiffers,
          isIgnored = false,
          typeName = (difflicious.utils.TypeName.apply[difflicious.testtypes.TreeContainer](
            "difflicious.testtypes.TreeContainer",
            "TreeContainer",
            Nil,
          ): difflicious.utils.TypeName[difflicious.testtypes.TreeContainer]),
          canUseEqualsValue =
            fieldDiffers.forall(((_$7: Tuple2[String, difflicious.Differ[Any]]) => _$7._2.canUseEquals)),
        ): difflicious.Differ[difflicious.testtypes.TreeContainer])
      }): difflicious.Differ[difflicious.testtypes.TreeContainer])
    lazy val derivedDiffer$macro$4: difflicious.Differ[difflicious.testtypes.TreeCaseClass1] = d1
    lazy val derivedDiffer$macro$6: difflicious.Differ[difflicious.testtypes.TreeCaseClass2] =
      (new difflicious.differ.LazyDiffer[difflicious.testtypes.TreeCaseClass2]({
        val `fieldDiffers₂`: Vector[Tuple2[String, difflicious.Differ[Any]]] = scala.Vector
          .empty[Tuple2[String, difflicious.Differ[Any]]]
          .:+[Tuple2[String, difflicious.Differ[Any]]](
            Tuple2.apply[String, difflicious.Differ[Any]](
              "value",
              derivedDiffer$macro$7.asInstanceOf[difflicious.Differ[Any]],
            ),
          )

        (new difflicious.differ.ProductDiffer[difflicious.testtypes.TreeCaseClass2](
          fieldDiffers = `fieldDiffers₂`,
          isIgnored = false,
          typeName = (difflicious.utils.TypeName.apply[difflicious.testtypes.TreeCaseClass2](
            "difflicious.testtypes.TreeCaseClass2",
            "TreeCaseClass2",
            Nil,
          ): difflicious.utils.TypeName[difflicious.testtypes.TreeCaseClass2]),
          canUseEqualsValue =
            `fieldDiffers₂`.forall(((`_$7₂`: Tuple2[String, difflicious.Differ[Any]]) => `_$7₂`._2.canUseEquals)),
        ): difflicious.Differ[difflicious.testtypes.TreeCaseClass2])
      }): difflicious.Differ[difflicious.testtypes.TreeCaseClass2])
    lazy val derivedDiffer$macro$7: difflicious.Differ[String] = difflicious.Differ.stringDiffer
    lazy val derivedDiffer$macro$8: difflicious.Differ[Option[difflicious.testtypes.TreeCaseClass2]] =
      difflicious.Differ
        .optionDiffer[difflicious.testtypes.TreeCaseClass2](derivedDiffer$macro$6)
        .asInstanceOf[difflicious.Differ[Option[difflicious.testtypes.TreeCaseClass2]]]
    lazy val derivedDiffer$macro$9
      : difflicious.Differ[Either[difflicious.testtypes.TreeCaseClass1, difflicious.testtypes.TreeCaseClass2]] =
      difflicious.Differ
        .eitherDiffer[difflicious.testtypes.TreeCaseClass1, difflicious.testtypes.TreeCaseClass2](
          derivedDiffer$macro$4,
          derivedDiffer$macro$6,
        )
        .asInstanceOf[difflicious.Differ[
          Either[difflicious.testtypes.TreeCaseClass1, difflicious.testtypes.TreeCaseClass2],
        ]]
    lazy val derivedDiffer$macro$10: difflicious.Differ[Map[String, difflicious.testtypes.TreeCaseClass2]] = {
      type Container[X, Y] = Map[String, difflicious.testtypes.TreeCaseClass2]
      val runtimeKeyDiffer: difflicious.differ.ValueDiffer[String] = difflicious.Differ.stringDiffer
      val runtimeValueDiffer: difflicious.Differ[difflicious.testtypes.TreeCaseClass2] = derivedDiffer$macro$6

      (new difflicious.differ.MapDiffer[[X >: Nothing <: Any, Y >: Nothing <: Any] =>> Map[
        String,
        difflicious.testtypes.TreeCaseClass2,
      ], String, difflicious.testtypes.TreeCaseClass2](
        isIgnored = false,
        keyDiffer = runtimeKeyDiffer,
        valueDiffer = runtimeValueDiffer,
        typeName = (difflicious.utils.TypeName.apply[Map[String, difflicious.testtypes.TreeCaseClass2]](
          "Map",
          "Map",
          scala.List.apply[difflicious.utils.TypeName[_ >: Nothing <: Any]](
            difflicious.utils.TypeName.apply[Any]("String", "String", Nil),
            difflicious.utils.TypeName.apply[Any]("difflicious.testtypes.TreeCaseClass2", "TreeCaseClass2", Nil),
          ),
        ): difflicious.utils.TypeName[Map[String, difflicious.testtypes.TreeCaseClass2]]),
        asMap = difflicious.utils.MapLike
          .stdMapAsMap[[K >: Nothing <: Any, V >: Nothing <: Any] =>> Map[K, V]]
          .asInstanceOf[difflicious.utils.MapLike[[X >: Nothing <: Any,
          Y >: Nothing <: Any] =>> Map[String, difflicious.testtypes.TreeCaseClass2]]],
        canUseEqualsValue = runtimeKeyDiffer.canUseEquals.&&(runtimeValueDiffer.canUseEquals),
      ): difflicious.differ.MapDiffer[[X >: Nothing <: Any, Y >: Nothing <: Any] =>> Map[
        String,
        difflicious.testtypes.TreeCaseClass2,
      ], String, difflicious.testtypes.TreeCaseClass2])
    }
    lazy val derivedDiffer$macro$11: difflicious.Differ[List[Option[difflicious.testtypes.TreeCaseClass2]]] = {
      type Container[X] = List[Option[difflicious.testtypes.TreeCaseClass2]]

      (difflicious.differ.SeqDiffer.create[[X >: Nothing <: Any] =>> List[
        Option[difflicious.testtypes.TreeCaseClass2],
      ], Option[difflicious.testtypes.TreeCaseClass2]](
        itemDiffer = derivedDiffer$macro$8,
        typeName = (difflicious.utils.TypeName.apply[List[Option[difflicious.testtypes.TreeCaseClass2]]](
          "List",
          "List",
          scala.List.apply[difflicious.utils.TypeName[_ >: Nothing <: Any]](
            difflicious.utils.TypeName.apply[Any](
              "Option",
              "Option",
              scala.List.apply[difflicious.utils.TypeName[_ >: Nothing <: Any]](
                difflicious.utils.TypeName.apply[Any]("difflicious.testtypes.TreeCaseClass2", "TreeCaseClass2", Nil),
              ),
            ),
          ),
        ): difflicious.utils.TypeName[List[Option[difflicious.testtypes.TreeCaseClass2]]]),
        asSeq = difflicious.utils.SeqLike
          .stdSeqAsSeq[[A >: Nothing <: Any] =>> List[A]]
          .asInstanceOf[difflicious.utils.SeqLike[[X >: Nothing <: Any] =>> List[
            Option[difflicious.testtypes.TreeCaseClass2],
          ]]],
      ): difflicious.differ.SeqDiffer[[X >: Nothing <: Any] =>> List[
        Option[difflicious.testtypes.TreeCaseClass2],
      ], Option[difflicious.testtypes.TreeCaseClass2]])
    }

    (derivedDiffer$macro$3: difflicious.Differ[difflicious.testtypes.TreeContainer])
  }: difflicious.Differ[difflicious.testtypes.TreeContainer])
}

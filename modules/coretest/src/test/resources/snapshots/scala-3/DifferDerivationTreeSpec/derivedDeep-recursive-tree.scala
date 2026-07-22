{
  val DifferGen_this: difflicious.Differ.type = difflicious.Differ

  ({
    lazy val derivedDiffer$macro$15: difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode] =
      (new difflicious.differ.LazyDiffer[difflicious.testtypes.RecursiveDerivedDeepNode]({
        val fieldDiffers: Vector[Tuple2[String, difflicious.Differ[Any]]] = scala.Vector
          .empty[Tuple2[String, difflicious.Differ[Any]]]
          .:+[Tuple2[String, difflicious.Differ[Any]]](
            Tuple2.apply[String, difflicious.Differ[Any]](
              "value",
              derivedDiffer$macro$16.asInstanceOf[difflicious.Differ[Any]],
            ),
          )
          .:+[Tuple2[String, difflicious.Differ[Any]]](
            Tuple2.apply[String, difflicious.Differ[Any]](
              "children",
              derivedDiffer$macro$17.asInstanceOf[difflicious.Differ[Any]],
            ),
          )

        (new difflicious.differ.ProductDiffer[difflicious.testtypes.RecursiveDerivedDeepNode](
          fieldDiffers = fieldDiffers,
          isIgnored = false,
          typeName = (difflicious.utils.TypeName.apply[difflicious.testtypes.RecursiveDerivedDeepNode](
            "difflicious.testtypes.RecursiveDerivedDeepNode",
            "RecursiveDerivedDeepNode",
            Nil,
          ): difflicious.utils.TypeName[difflicious.testtypes.RecursiveDerivedDeepNode]),
          canUseEqualsValue =
            fieldDiffers.forall(((_$7: Tuple2[String, difflicious.Differ[Any]]) => _$7._2.canUseEquals)),
        ): difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])
      }): difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])
    lazy val derivedDiffer$macro$16: difflicious.Differ[String] = difflicious.Differ.stringDiffer
    lazy val derivedDiffer$macro$17: difflicious.Differ[List[difflicious.testtypes.RecursiveDerivedDeepNode]] = {
      type Container[X] = List[difflicious.testtypes.RecursiveDerivedDeepNode]

      (difflicious.differ.SeqDiffer.create[[X >: Nothing <: Any] =>> List[
        difflicious.testtypes.RecursiveDerivedDeepNode,
      ], difflicious.testtypes.RecursiveDerivedDeepNode](
        itemDiffer = derivedDiffer$macro$15,
        typeName = (difflicious.utils.TypeName.apply[List[difflicious.testtypes.RecursiveDerivedDeepNode]](
          "List",
          "List",
          scala.List.apply[difflicious.utils.TypeName[_ >: Nothing <: Any]](
            difflicious.utils.TypeName
              .apply[Any]("difflicious.testtypes.RecursiveDerivedDeepNode", "RecursiveDerivedDeepNode", Nil),
          ),
        ): difflicious.utils.TypeName[List[difflicious.testtypes.RecursiveDerivedDeepNode]]),
        asSeq = difflicious.utils.SeqLike
          .stdSeqAsSeq[[A >: Nothing <: Any] =>> List[A]]
          .asInstanceOf[difflicious.utils.SeqLike[[X >: Nothing <: Any] =>> List[
            difflicious.testtypes.RecursiveDerivedDeepNode,
          ]]],
      ): difflicious.differ.SeqDiffer[[X >: Nothing <: Any] =>> List[
        difflicious.testtypes.RecursiveDerivedDeepNode,
      ], difflicious.testtypes.RecursiveDerivedDeepNode])
    }

    (derivedDiffer$macro$15: difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])
  }: difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])
}

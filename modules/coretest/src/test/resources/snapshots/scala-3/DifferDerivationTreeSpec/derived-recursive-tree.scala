{
  val DifferGen_this: difflicious.Differ.type = difflicious.Differ

  ({
    lazy val derivedDiffer$macro$12: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode] = (new difflicious.differ.LazyDiffer[difflicious.testtypes.RecursiveDerivedNode]({
      val fieldDiffers: Vector[Tuple2[String, difflicious.Differ[Any]]] = scala.Vector.empty[Tuple2[String, difflicious.Differ[Any]]].:+[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("value", derivedDiffer$macro$13.asInstanceOf[difflicious.Differ[Any]])).:+[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("children", derivedDiffer$macro$14.asInstanceOf[difflicious.Differ[Any]]))

      (new difflicious.differ.ProductDiffer[difflicious.testtypes.RecursiveDerivedNode](fieldDiffers = fieldDiffers, isIgnored = false, typeName = (difflicious.utils.TypeName.apply[difflicious.testtypes.RecursiveDerivedNode]("difflicious.testtypes.RecursiveDerivedNode", "RecursiveDerivedNode", Nil): difflicious.utils.TypeName[difflicious.testtypes.RecursiveDerivedNode]), canUseEqualsValue = fieldDiffers.forall(((_$7: Tuple2[String, difflicious.Differ[Any]]) => _$7._2.canUseEquals))): difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
    }): difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
    lazy val derivedDiffer$macro$13: difflicious.Differ[String] = difflicious.Differ.stringDiffer
    lazy val derivedDiffer$macro$14: difflicious.Differ[List[difflicious.testtypes.RecursiveDerivedNode]] = {
      type Container[X] = List[difflicious.testtypes.RecursiveDerivedNode]

      (difflicious.differ.SeqDiffer.create[[X >: Nothing <: Any] =>> List[difflicious.testtypes.RecursiveDerivedNode], difflicious.testtypes.RecursiveDerivedNode](itemDiffer = derivedDiffer$macro$12, typeName = (difflicious.utils.TypeName.apply[List[difflicious.testtypes.RecursiveDerivedNode]]("List", "List", scala.List.apply[difflicious.utils.TypeName[_ >: Nothing <: Any]](difflicious.utils.TypeName.apply[Any]("difflicious.testtypes.RecursiveDerivedNode", "RecursiveDerivedNode", Nil))): difflicious.utils.TypeName[List[difflicious.testtypes.RecursiveDerivedNode]]), asSeq = difflicious.utils.SeqLike.stdSeqAsSeq[[A >: Nothing <: Any] =>> List[A]].asInstanceOf[difflicious.utils.SeqLike[[X >: Nothing <: Any] =>> List[difflicious.testtypes.RecursiveDerivedNode]]]): difflicious.differ.SeqDiffer[[X >: Nothing <: Any] =>> List[difflicious.testtypes.RecursiveDerivedNode], difflicious.testtypes.RecursiveDerivedNode])
    }

    (derivedDiffer$macro$12: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
  }: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
}

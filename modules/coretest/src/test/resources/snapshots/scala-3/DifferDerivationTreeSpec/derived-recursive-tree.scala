{
  val DifferGen_this: difflicious.Differ.type = difflicious.Differ

  ({
    lazy val derivedDiffer$macro$12: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode] = (new difflicious.differ.LazyDiffer[difflicious.testtypes.RecursiveDerivedNode]((new difflicious.differ.ProductDiffer[difflicious.testtypes.RecursiveDerivedNode](scala.Vector.empty[Tuple2[String, difflicious.Differ[Any]]].:+[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("value", derivedDiffer$macro$13.asInstanceOf[difflicious.Differ[Any]])).:+[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("children", derivedDiffer$macro$14.asInstanceOf[difflicious.Differ[Any]])), false, (difflicious.utils.TypeName.apply[difflicious.testtypes.RecursiveDerivedNode]("difflicious.testtypes.RecursiveDerivedNode", "RecursiveDerivedNode", Nil): difflicious.utils.TypeName[difflicious.testtypes.RecursiveDerivedNode])): difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])): difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
    lazy val derivedDiffer$macro$13: difflicious.Differ[String] = difflicious.Differ.stringDiffer
    lazy val derivedDiffer$macro$14: difflicious.Differ[List[difflicious.testtypes.RecursiveDerivedNode]] = differ.SeqDiffer.create[[A >: Nothing <: Any] =>> List[A], difflicious.testtypes.RecursiveDerivedNode](derivedDiffer$macro$12, (difflicious.utils.TypeName.apply[List[difflicious.testtypes.RecursiveDerivedNode]]("List", "List", scala.List.apply[difflicious.utils.TypeName[_ >: Nothing <: Any]](difflicious.utils.TypeName.apply[Any]("difflicious.testtypes.RecursiveDerivedNode", "RecursiveDerivedNode", Nil))): difflicious.utils.TypeName[List[difflicious.testtypes.RecursiveDerivedNode]]), difflicious.utils.SeqLike.stdSeqAsSeq[[A >: Nothing <: Any] =>> List[A]])

    (derivedDiffer$macro$12: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
  }: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
}

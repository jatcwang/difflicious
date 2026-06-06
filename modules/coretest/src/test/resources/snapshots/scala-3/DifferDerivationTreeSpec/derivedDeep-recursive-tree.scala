{
  val DifferGen_this: difflicious.Differ.type = difflicious.Differ

  ({
    lazy val derivedDiffer$macro$15: difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode] = (new difflicious.differ.LazyDiffer[difflicious.testtypes.RecursiveDerivedDeepNode]((new difflicious.differ.ProductDiffer[difflicious.testtypes.RecursiveDerivedDeepNode](scala.Vector.empty[Tuple2[String, difflicious.Differ[Any]]].:+[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("value", derivedDiffer$macro$16.asInstanceOf[difflicious.Differ[Any]])).:+[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("children", derivedDiffer$macro$17.asInstanceOf[difflicious.Differ[Any]])), false, (difflicious.utils.TypeName.apply[difflicious.testtypes.RecursiveDerivedDeepNode]("difflicious.testtypes.RecursiveDerivedDeepNode", "RecursiveDerivedDeepNode", Nil): difflicious.utils.TypeName[difflicious.testtypes.RecursiveDerivedDeepNode])): difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])): difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])
    lazy val derivedDiffer$macro$16: difflicious.Differ[String] = difflicious.Differ.stringDiffer
    lazy val derivedDiffer$macro$17: difflicious.Differ[List[difflicious.testtypes.RecursiveDerivedDeepNode]] = differ.SeqDiffer.create[[A >: Nothing <: Any] =>> List[A], difflicious.testtypes.RecursiveDerivedDeepNode](derivedDiffer$macro$15, (difflicious.utils.TypeName.apply[List[difflicious.testtypes.RecursiveDerivedDeepNode]]("List", "List", scala.List.apply[difflicious.utils.TypeName[_ >: Nothing <: Any]](difflicious.utils.TypeName.apply[Any]("difflicious.testtypes.RecursiveDerivedDeepNode", "RecursiveDerivedDeepNode", Nil))): difflicious.utils.TypeName[List[difflicious.testtypes.RecursiveDerivedDeepNode]]), difflicious.utils.SeqLike.stdSeqAsSeq[[A >: Nothing <: Any] =>> List[A]])

    (derivedDiffer$macro$15: difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])
  }: difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])
}

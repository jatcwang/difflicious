{
  val DifferGen_this: difflicious.Differ.type = difflicious.Differ

  ({
    lazy val derivedDiffer$macro$12: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode] = (new difflicious.differ.LazyDiffer[difflicious.testtypes.RecursiveDerivedNode]((new difflicious.differ.ProductDiffer[difflicious.testtypes.RecursiveDerivedNode](scala.Vector.empty[Tuple2[String, difflicious.Differ[Any]]].:+[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("value", derivedDiffer$macro$13.asInstanceOf[difflicious.Differ[Any]])).:+[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("children", derivedDiffer$macro$14.asInstanceOf[difflicious.Differ[Any]])), false, difflicious.utils.TypeName.apply[Any](long = "difflicious.testtypes.RecursiveDerivedNode", short = "RecursiveDerivedNode", typeArguments = Nil)): difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])): difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
    lazy val derivedDiffer$macro$13: difflicious.Differ[String] = difflicious.Differ.stringDiffer
    lazy val derivedDiffer$macro$14: difflicious.Differ[List[difflicious.testtypes.RecursiveDerivedNode]] = differ.SeqDiffer.create[[A >: Nothing <: Any] =>> List[A], difflicious.testtypes.RecursiveDerivedNode](derivedDiffer$macro$12, difflicious.utils.TypeName.apply[Any](long = "List", short = "List", typeArguments = Nil), difflicious.utils.SeqLike.stdSeqAsSeq[[A >: Nothing <: Any] =>> List[A]])

    (derivedDiffer$macro$12: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
  }: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
}

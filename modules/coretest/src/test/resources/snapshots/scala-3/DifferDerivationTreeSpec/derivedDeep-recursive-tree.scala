{
  val DifferGen_this: difflicious.Differ.type = difflicious.Differ

  ({
    lazy val derivedDiffer$macro$5: difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode] = new difflicious.differ.LazyDiffer[difflicious.testtypes.RecursiveDerivedDeepNode](new difflicious.differ.ProductDiffer[difflicious.testtypes.RecursiveDerivedDeepNode](fieldDiffers = scala.Vector.apply[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("value", difflicious.Differ.stringDiffer.asInstanceOf[difflicious.Differ[Any]]), Tuple2.apply[String, difflicious.Differ[Any]]("children", new difflicious.differ.SeqDiffer[List, difflicious.testtypes.RecursiveDerivedDeepNode][[A >: Nothing <: Any] =>> List[A], difflicious.testtypes.RecursiveDerivedDeepNode](false, difflicious.ConfigureOp.PairBy.Index, derivedDiffer$macro$5, difflicious.utils.TypeName.apply[List[difflicious.testtypes.RecursiveDerivedDeepNode]](/*irrelevant*/), difflicious.utils.SeqLike.stdSeqAsSeq[[A >: Nothing <: Any] =>> List[A]]).asInstanceOf[difflicious.Differ[Any]])), isIgnored = false, typeName = difflicious.utils.TypeName.apply[Any](long = "difflicious.testtypes.RecursiveDerivedDeepNode", short = "RecursiveDerivedDeepNode", typeArguments = Nil)))

    (derivedDiffer$macro$5: difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])
  }: difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])
}

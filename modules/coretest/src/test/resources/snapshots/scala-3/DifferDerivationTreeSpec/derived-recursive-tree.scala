{
  val DifferGen_this: difflicious.Differ.type = difflicious.Differ

  ({
    lazy val derivedDiffer$macro$4: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode] = new difflicious.differ.LazyDiffer[difflicious.testtypes.RecursiveDerivedNode](new difflicious.differ.ProductDiffer[difflicious.testtypes.RecursiveDerivedNode](fieldDiffers = scala.Vector.apply[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("value", difflicious.Differ.stringDiffer.asInstanceOf[difflicious.Differ[Any]]), Tuple2.apply[String, difflicious.Differ[Any]]("children", new difflicious.differ.SeqDiffer[List, difflicious.testtypes.RecursiveDerivedNode][[A >: Nothing <: Any] =>> List[A], difflicious.testtypes.RecursiveDerivedNode](false, difflicious.ConfigureOp.PairBy.Index, derivedDiffer$macro$4, difflicious.utils.TypeName.apply[List[difflicious.testtypes.RecursiveDerivedNode]](/*irrelevant*/), difflicious.utils.SeqLike.stdSeqAsSeq[[A >: Nothing <: Any] =>> List[A]]).asInstanceOf[difflicious.Differ[Any]])), isIgnored = false, typeName = difflicious.utils.TypeName.apply[Any](long = "difflicious.testtypes.RecursiveDerivedNode", short = "RecursiveDerivedNode", typeArguments = Nil)))

    (derivedDiffer$macro$4: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
  }: difflicious.Differ[difflicious.testtypes.RecursiveDerivedNode])
}

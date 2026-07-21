{
  lazy val derivedDiffer$macro$15: difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode] = ((new difflicious.differ.LazyDiffer[difflicious.testtypes.RecursiveDerivedDeepNode]({
    val fieldDiffers = Vector.empty[(String, difflicious.Differ[Any])].:+[(String, difflicious.Differ[Any])](Tuple2.apply[String, difflicious.Differ[Any]]("value", derivedDiffer$macro$16.asInstanceOf[difflicious.Differ[Any]])).:+[(String, difflicious.Differ[Any])](Tuple2.apply[String, difflicious.Differ[Any]]("children", derivedDiffer$macro$17.asInstanceOf[difflicious.Differ[Any]]));
    ((new difflicious.differ.ProductDiffer[difflicious.testtypes.RecursiveDerivedDeepNode](fieldDiffers, false, ((difflicious.utils.TypeName.apply[difflicious.testtypes.RecursiveDerivedDeepNode]("difflicious.testtypes.RecursiveDerivedDeepNode", "RecursiveDerivedDeepNode", Nil)): difflicious.utils.TypeName[difflicious.testtypes.RecursiveDerivedDeepNode]), fieldDiffers.forall(((x$13: Tuple2[String, difflicious.Differ[Any]]) => x$13._2.canUseEquals)))): difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode])
  })): difflicious.Differ[difflicious.testtypes.RecursiveDerivedDeepNode]);
  lazy val derivedDiffer$macro$16: difflicious.Differ[String] = difflicious.Differ.stringDiffer;
  lazy val derivedDiffer$macro$17: difflicious.Differ[List[difflicious.testtypes.RecursiveDerivedDeepNode]] = {
    type Container[X] = List[difflicious.testtypes.RecursiveDerivedDeepNode];
    difflicious.differ.SeqDiffer.create[Container, difflicious.testtypes.RecursiveDerivedDeepNode](derivedDiffer$macro$15, ((difflicious.utils.TypeName.apply[List[difflicious.testtypes.RecursiveDerivedDeepNode]]("List", "List", List.apply[(difflicious.utils.TypeName[_$15] forSome {
      type _$15
    })](difflicious.utils.TypeName.apply[Any]("difflicious.testtypes.RecursiveDerivedDeepNode", "RecursiveDerivedDeepNode", Nil)))): difflicious.utils.TypeName[List[difflicious.testtypes.RecursiveDerivedDeepNode]]), difflicious.utils.SeqLike.stdSeqAsSeq[[+A]List[A]].asInstanceOf[difflicious.utils.SeqLike[Container]])
  };
  derivedDiffer$macro$15
}

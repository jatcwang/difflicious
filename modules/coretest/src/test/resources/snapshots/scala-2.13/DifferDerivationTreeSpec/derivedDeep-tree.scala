{
  lazy val derivedDiffer$macro$2: difflicious.Differ[difflicious.testtypes.TreeContainer] = new difflicious.differ.LazyDiffer[difflicious.testtypes.TreeContainer](new difflicious.differ.ProductDiffer[difflicious.testtypes.TreeContainer](Vector.apply[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("c1", d1.asInstanceOf[difflicious.Differ[Any]]), Tuple2.apply[String, difflicious.Differ[Any]]("c2", derivedDiffer$macro$3.asInstanceOf[difflicious.Differ[Any]]), Tuple2.apply[String, difflicious.Differ[Any]]("option", ({
  implicit val valueDiffer: difflicious.Differ[difflicious.testtypes.TreeCaseClass2] = derivedDiffer$macro$3;
  difflicious.Differ.optionDiffer[difflicious.testtypes.TreeCaseClass2](valueDiffer)
}).asInstanceOf[difflicious.Differ[Any]]), Tuple2.apply[String, difflicious.Differ[Any]]("either", ({
  implicit val leftValueDiffer: difflicious.Differ[difflicious.testtypes.TreeCaseClass1] = d1;
  implicit val rightValueDiffer: difflicious.Differ[difflicious.testtypes.TreeCaseClass2] = derivedDiffer$macro$3;
  difflicious.Differ.eitherDiffer[difflicious.testtypes.TreeCaseClass1, difflicious.testtypes.TreeCaseClass2](leftValueDiffer, rightValueDiffer)
}).asInstanceOf[difflicious.Differ[Any]]), Tuple2.apply[String, difflicious.Differ[Any]]("map", new difflicious.differ.MapDiffer[Map,String,difflicious.testtypes.TreeCaseClass2](false, difflicious.Differ.stringDiffer, derivedDiffer$macro$3, difflicious.utils.TypeName.apply[Map[String,difflicious.testtypes.TreeCaseClass2]](/*irrelevant*/), difflicious.utils.MapLike.stdMapAsMap[[K, +V]Map[K,V]]).asInstanceOf[difflicious.Differ[Any]]), Tuple2.apply[String, difflicious.Differ[Any]]("list", new difflicious.differ.SeqDiffer[List,Option[difflicious.testtypes.TreeCaseClass2]](false, difflicious.ConfigureOp.PairBy.Index, {
  implicit val valueDiffer: difflicious.Differ[difflicious.testtypes.TreeCaseClass2] = derivedDiffer$macro$3;
  difflicious.Differ.optionDiffer[difflicious.testtypes.TreeCaseClass2](valueDiffer)
}, difflicious.utils.TypeName.apply[List[Option[difflicious.testtypes.TreeCaseClass2]]](/*irrelevant*/), difflicious.utils.SeqLike.stdSeqAsSeq[[+A]List[A]]).asInstanceOf[difflicious.Differ[Any]])), false, difflicious.utils.TypeName.apply[Any]("difflicious.testtypes.TreeContainer", "TreeContainer", Nil)));
  lazy val derivedDiffer$macro$3: difflicious.Differ[difflicious.testtypes.TreeCaseClass2] = new difflicious.differ.LazyDiffer[difflicious.testtypes.TreeCaseClass2](new difflicious.differ.ProductDiffer[difflicious.testtypes.TreeCaseClass2](Vector.apply[Tuple2[String, difflicious.Differ[Any]]](Tuple2.apply[String, difflicious.Differ[Any]]("value", difflicious.Differ.stringDiffer.asInstanceOf[difflicious.Differ[Any]])), false, difflicious.utils.TypeName.apply[Any]("difflicious.testtypes.TreeCaseClass2", "TreeCaseClass2", Nil)));
  derivedDiffer$macro$2
}

{
  lazy val derivedDiffer$macro$3: difflicious.Differ[difflicious.testtypes.TreeContainer] = ((new difflicious.differ.LazyDiffer[difflicious.testtypes.TreeContainer]({
    val fieldDiffers = Vector.empty[(String, difflicious.Differ[Any])].:+[(String, difflicious.Differ[Any])](Tuple2.apply[String, difflicious.Differ[Any]]("c1", derivedDiffer$macro$4.asInstanceOf[difflicious.Differ[Any]])).:+[(String, difflicious.Differ[Any])](Tuple2.apply[String, difflicious.Differ[Any]]("c2", derivedDiffer$macro$6.asInstanceOf[difflicious.Differ[Any]])).:+[(String, difflicious.Differ[Any])](Tuple2.apply[String, difflicious.Differ[Any]]("option", derivedDiffer$macro$8.asInstanceOf[difflicious.Differ[Any]])).:+[(String, difflicious.Differ[Any])](Tuple2.apply[String, difflicious.Differ[Any]]("either", derivedDiffer$macro$9.asInstanceOf[difflicious.Differ[Any]])).:+[(String, difflicious.Differ[Any])](Tuple2.apply[String, difflicious.Differ[Any]]("map", derivedDiffer$macro$10.asInstanceOf[difflicious.Differ[Any]])).:+[(String, difflicious.Differ[Any])](Tuple2.apply[String, difflicious.Differ[Any]]("list", derivedDiffer$macro$11.asInstanceOf[difflicious.Differ[Any]]));
    ((new difflicious.differ.ProductDiffer[difflicious.testtypes.TreeContainer](fieldDiffers, false, ((difflicious.utils.TypeName.apply[difflicious.testtypes.TreeContainer]("difflicious.testtypes.TreeContainer", "TreeContainer", Nil)): difflicious.utils.TypeName[difflicious.testtypes.TreeContainer]), fieldDiffers.forall(((x$13: Tuple2[String, difflicious.Differ[Any]]) => x$13._2.canUseEquals)))): difflicious.Differ[difflicious.testtypes.TreeContainer])
  })): difflicious.Differ[difflicious.testtypes.TreeContainer]);
  lazy val derivedDiffer$macro$4: difflicious.Differ[difflicious.testtypes.TreeCaseClass1] = d1;
  lazy val derivedDiffer$macro$6: difflicious.Differ[difflicious.testtypes.TreeCaseClass2] = ((new difflicious.differ.LazyDiffer[difflicious.testtypes.TreeCaseClass2]({
    val fieldDiffers = Vector.empty[(String, difflicious.Differ[Any])].:+[(String, difflicious.Differ[Any])](Tuple2.apply[String, difflicious.Differ[Any]]("value", derivedDiffer$macro$7.asInstanceOf[difflicious.Differ[Any]]));
    ((new difflicious.differ.ProductDiffer[difflicious.testtypes.TreeCaseClass2](fieldDiffers, false, ((difflicious.utils.TypeName.apply[difflicious.testtypes.TreeCaseClass2]("difflicious.testtypes.TreeCaseClass2", "TreeCaseClass2", Nil)): difflicious.utils.TypeName[difflicious.testtypes.TreeCaseClass2]), fieldDiffers.forall(((x$13: Tuple2[String, difflicious.Differ[Any]]) => x$13._2.canUseEquals)))): difflicious.Differ[difflicious.testtypes.TreeCaseClass2])
  })): difflicious.Differ[difflicious.testtypes.TreeCaseClass2]);
  lazy val derivedDiffer$macro$7: difflicious.Differ[String] = difflicious.Differ.stringDiffer;
  lazy val derivedDiffer$macro$8: difflicious.Differ[Option[difflicious.testtypes.TreeCaseClass2]] = difflicious.Differ.optionDiffer[difflicious.testtypes.TreeCaseClass2](derivedDiffer$macro$6).asInstanceOf[difflicious.Differ[Option[difflicious.testtypes.TreeCaseClass2]]];
  lazy val derivedDiffer$macro$9: difflicious.Differ[Either[difflicious.testtypes.TreeCaseClass1,difflicious.testtypes.TreeCaseClass2]] = difflicious.Differ.eitherDiffer[difflicious.testtypes.TreeCaseClass1, difflicious.testtypes.TreeCaseClass2](derivedDiffer$macro$4, derivedDiffer$macro$6).asInstanceOf[difflicious.Differ[Either[difflicious.testtypes.TreeCaseClass1,difflicious.testtypes.TreeCaseClass2]]];
  lazy val derivedDiffer$macro$10: difflicious.Differ[Map[String,difflicious.testtypes.TreeCaseClass2]] = {
    type Container[X, Y] = Map[String,difflicious.testtypes.TreeCaseClass2];
    val runtimeKeyDiffer: difflicious.differ.ValueDiffer[String] = difflicious.Differ.stringDiffer;
    val runtimeValueDiffer: difflicious.Differ[difflicious.testtypes.TreeCaseClass2] = derivedDiffer$macro$6;
    new difflicious.differ.MapDiffer[Container, String, difflicious.testtypes.TreeCaseClass2](false, runtimeKeyDiffer, runtimeValueDiffer, ((difflicious.utils.TypeName.apply[Map[String,difflicious.testtypes.TreeCaseClass2]]("Map", "Map", List.apply[(difflicious.utils.TypeName[_$6] forSome {
      type _$6
    })](difflicious.utils.TypeName.apply[Any]("String", "String", Nil), difflicious.utils.TypeName.apply[Any]("difflicious.testtypes.TreeCaseClass2", "TreeCaseClass2", Nil)))): difflicious.utils.TypeName[Map[String,difflicious.testtypes.TreeCaseClass2]]), difflicious.utils.MapLike.stdMapAsMap[[K, +V]Map[K,V]].asInstanceOf[difflicious.utils.MapLike[Container]], runtimeKeyDiffer.canUseEquals.&&(runtimeValueDiffer.canUseEquals))
  };
  lazy val derivedDiffer$macro$11: difflicious.Differ[List[Option[difflicious.testtypes.TreeCaseClass2]]] = {
    type Container[X] = List[Option[difflicious.testtypes.TreeCaseClass2]];
    difflicious.differ.SeqDiffer.create[Container, Option[difflicious.testtypes.TreeCaseClass2]](derivedDiffer$macro$8, ((difflicious.utils.TypeName.apply[List[Option[difflicious.testtypes.TreeCaseClass2]]]("List", "List", List.apply[(difflicious.utils.TypeName[_$9] forSome {
      type _$9
    })](difflicious.utils.TypeName.apply[Any]("Option", "Option", List.apply[(difflicious.utils.TypeName[_$8] forSome {
      type _$8
    })](difflicious.utils.TypeName.apply[Any]("difflicious.testtypes.TreeCaseClass2", "TreeCaseClass2", Nil)))))): difflicious.utils.TypeName[List[Option[difflicious.testtypes.TreeCaseClass2]]]), difflicious.utils.SeqLike.stdSeqAsSeq[[+A]List[A]].asInstanceOf[difflicious.utils.SeqLike[Container]])
  };
  derivedDiffer$macro$3
}

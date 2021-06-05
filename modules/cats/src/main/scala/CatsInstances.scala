import cats.data.{NonEmptyMap, NonEmptyVector, NonEmptySet, NonEmptyChain, Chain, NonEmptyList}
import difflicious.Differ
import difflicious.Differ.{ValueDiffer, SeqDiffer, MapDiffer, SetDiffer}
import difflicious.utils.{TypeName, AsMap, AsSeq, AsSet}
import izumi.reflect.macrortti.LTag

trait CatsInstances {
  protected def nonEmptyMapTypeName(args: List[TypeName]): TypeName =
    TypeName("cats.data.NonEmptyMap", "NonEmptyMap", args)

  implicit val nonEmptyMapAsMap: AsMap[NonEmptyMap] = new AsMap[NonEmptyMap] {
    override def asMap[A, B](m: NonEmptyMap[A, B]): Map[A, B] = m.toSortedMap
  }

  implicit val nonEmptyListAsSeq: AsSeq[NonEmptyList] = new AsSeq[NonEmptyList] {
    override def asSeq[A](f: NonEmptyList[A]): Seq[A] = f.toList
  }

  implicit val nonEmptyVectorAsSeq: AsSeq[NonEmptyVector] = new AsSeq[NonEmptyVector] {
    override def asSeq[A](f: NonEmptyVector[A]): Seq[A] = f.toVector
  }

  implicit val chainAsSeq: AsSeq[Chain] = new AsSeq[Chain] {
    override def asSeq[A](f: Chain[A]): Seq[A] = f.toVector
  }

  implicit val nonEmptyChainAsSeq: AsSeq[NonEmptyChain] = new AsSeq[NonEmptyChain] {
    override def asSeq[A](f: NonEmptyChain[A]): Seq[A] = f.toChain.toVector
  }

  implicit val nonEmptySetAsSet: AsSet[NonEmptySet] = new AsSet[NonEmptySet] {
    override def asSet[A](f: NonEmptySet[A]): Set[A] = f.toSortedSet
  }

  implicit def nonEmptyMapDiffer[K, V](
    implicit keyDiffer: ValueDiffer[K],
    valueDiffer: Differ[V],
    tag: LTag[NonEmptyMap[K, V]],
    kTag: LTag[K],
    vTag: LTag[V],
  ): MapDiffer[NonEmptyMap, K, V] = new MapDiffer[NonEmptyMap, K, V](
    isIgnored = false,
    keyDiffer = keyDiffer,
    valueDiffer = valueDiffer,
    tag = tag,
    valueTag = vTag,
    typeName = TypeName("cats.data.NonEmptyMap", "NonEmptyMap", List(kTag.tag, vTag.tag).map(TypeName.fromLightTypeTag)),
    asMap = nonEmptyMapAsMap,
  )

  implicit def nonEmptyListDiffer[A](
    implicit aDiffer: Differ[A],
    tag: LTag[NonEmptyList[A]],
    itemTag: LTag[A],
  ): SeqDiffer[NonEmptyList, A] = {
    val typeName = TypeName("cats.data.NonEmptyList", "NonEmptyList", List(TypeName.fromLightTypeTag(itemTag.tag)))
    SeqDiffer.create(
      itemDiffer = aDiffer,
      typeName = typeName,
      asSeq = nonEmptyListAsSeq,
    )
  }

  implicit def nonEmptyVectorDiffer[A](
    implicit aDiffer: Differ[A],
    tag: LTag[NonEmptyVector[A]],
    itemTag: LTag[A],
  ): SeqDiffer[NonEmptyVector, A] = {
    val typeName = TypeName("cats.data.NonEmptyVector", "NonEmptyVector", List(TypeName.fromLightTypeTag(itemTag.tag)))
    SeqDiffer.create(
      itemDiffer = aDiffer,
      typeName = typeName,
      asSeq = nonEmptyVectorAsSeq,
    )
  }

  implicit def nonEmptyChainDiffer[A](
    implicit aDiffer: Differ[A],
    tag: LTag[NonEmptyChain[A]],
    itemTag: LTag[A],
  ): SeqDiffer[NonEmptyChain, A] = {
    val typeName = TypeName("cats.data.NonEmptyChain", "NonEmptyChain", List(TypeName.fromLightTypeTag(itemTag.tag)))
    SeqDiffer.create(
      itemDiffer = aDiffer,
      typeName = typeName,
      asSeq = nonEmptyChainAsSeq,
    )
  }

  implicit def nonEmptySetDiffer[A](
    implicit aDiffer: Differ[A],
    tag: LTag[NonEmptySet[A]],
    aTag: LTag[A],
  ): SetDiffer[NonEmptySet, A] = {
    val typeName = TypeName("cats.data.NonEmptySet", "NonEmptySet", List(TypeName.fromLightTypeTag(aTag.tag)))
    SetDiffer.create(
      itemDiffer = aDiffer,
      typeName = typeName,
      asSet = nonEmptySetAsSet,
    )
  }

}

object CatsInstances extends CatsInstances

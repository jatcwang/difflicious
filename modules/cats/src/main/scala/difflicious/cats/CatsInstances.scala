package difflicious.cats

import _root_.cats.data._
import difflicious.Differ
import difflicious.differ.{ValueDiffer, SeqDiffer, MapDiffer, SetDiffer}
import difflicious.utils._
import izumi.reflect.macrortti.LTag

trait CatsInstances {
  protected def nonEmptyMapTypeName(args: List[TypeName]): TypeName =
    TypeName("cats.data.NonEmptyMap", "NonEmptyMap", args)

  implicit val nonEmptyMapAsMap: MapLike[NonEmptyMap] = new MapLike[NonEmptyMap] {
    override def asMap[A, B](m: NonEmptyMap[A, B]): Map[A, B] = m.toSortedMap
  }

  implicit def nonEmptyMapEachable[K]: Eachable[NonEmptyMap[K, *]] = new Eachable[NonEmptyMap[K, *]] {}

  implicit val nonEmptyListAsSeq: SeqLike[NonEmptyList] = new SeqLike[NonEmptyList] {
    override def asSeq[A](f: NonEmptyList[A]): Seq[A] = f.toList
  }

  implicit val nonEmptyVectorAsSeq: SeqLike[NonEmptyVector] = new SeqLike[NonEmptyVector] {
    override def asSeq[A](f: NonEmptyVector[A]): Seq[A] = f.toVector
  }

  implicit val chainAsSeq: SeqLike[Chain] = new SeqLike[Chain] {
    override def asSeq[A](f: Chain[A]): Seq[A] = f.toVector
  }

  implicit val nonEmptyChainAsSeq: SeqLike[NonEmptyChain] = new SeqLike[NonEmptyChain] {
    override def asSeq[A](f: NonEmptyChain[A]): Seq[A] = f.toChain.toVector
  }

  implicit val nonEmptySetAsSet: SetLike[NonEmptySet] = new SetLike[NonEmptySet] {
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

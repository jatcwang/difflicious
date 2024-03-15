package difflicious.cats

import _root_.cats.data._
import difflicious.Differ
import difflicious.differ.{ValueDiffer, SeqDiffer, MapDiffer, SetDiffer}
import difflicious.utils._

trait CatsInstances {
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
    typeName: TypeName[NonEmptyMap[K, V]],
  ): MapDiffer[NonEmptyMap, K, V] = new MapDiffer[NonEmptyMap, K, V](
    isIgnored = false,
    keyDiffer = keyDiffer,
    valueDiffer = valueDiffer,
    typeName = typeName.copy(long = "cats.data.NonEmptyMap", short = "NonEmptyMap"),
    asMap = nonEmptyMapAsMap,
  )

  implicit def nonEmptyListDiffer[A](
    implicit aDiffer: Differ[A],
    typeName: TypeName[NonEmptyList[A]],
  ): SeqDiffer[NonEmptyList, A] = {
    SeqDiffer.create(
      itemDiffer = aDiffer,
      typeName = typeName.copy(
        long = "cats.data.NonEmptyList",
        short = "NonEmptyList",
      ),
      asSeq = nonEmptyListAsSeq,
    )
  }

  implicit def nonEmptyVectorDiffer[A](
    implicit aDiffer: Differ[A],
    typeName: TypeName[NonEmptyVector[A]],
  ): SeqDiffer[NonEmptyVector, A] = {
    SeqDiffer.create(
      itemDiffer = aDiffer,
      typeName = typeName.copy(
        long = "cats.data.NonEmptyVector",
        short = "NonEmptyVector",
      ),
      asSeq = nonEmptyVectorAsSeq,
    )
  }

  implicit def nonEmptyChainDiffer[A](
    implicit aDiffer: Differ[A],
    typeName: TypeName[NonEmptyChain[A]],
  ): SeqDiffer[NonEmptyChain, A] = {
    SeqDiffer.create(
      itemDiffer = aDiffer,
      typeName = typeName.copy(
        long = "cats.data.NonEmptyChain",
        short = "NonEmptyChain",
      ),
      asSeq = nonEmptyChainAsSeq,
    )
  }

  implicit def nonEmptySetDiffer[A](
    implicit aDiffer: Differ[A],
    typeName: TypeName[NonEmptySet[A]],
  ): SetDiffer[NonEmptySet, A] = {
    SetDiffer.create(
      itemDiffer = aDiffer,
      typeName = typeName.copy(
        long = "cats.data.NonEmptySet",
        short = "NonEmptySet",
      ),
      asSet = nonEmptySetAsSet,
    )
  }

  implicit def chainDiffer[A](
    implicit aDiffer: Differ[A],
    typeName: TypeName[Chain[A]],
  ): SeqDiffer[Chain, A] = {
    SeqDiffer.create(
      itemDiffer = aDiffer,
      typeName = typeName.copy(
        long = "cats.data.Chain",
        short = "Chain",
      ),
      asSeq = chainAsSeq,
    )
  }

  implicit def validatedDiffer[E: Differ, A: Differ]: Differ[Validated[E, A]] = Differ.derived

}

object CatsInstances extends CatsInstances

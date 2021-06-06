package difflicious
import difflicious.ConfigureError.TypeTagMismatch
import difflicious.ConfigureOp.PairBy
import difflicious.differ._
import difflicious.internal.ConfigureMethods
import difflicious.utils.{TypeName, MapLike, SetLike, SeqLike}
import izumi.reflect.macrortti.LTag

trait Differ[T] extends ConfigureMethods[T] {
  type R <: DiffResult

  // Type tag of T. Required for runtime typechecking
  protected def tag: LTag[T]

  def diff(inputs: DiffInput[T]): R

  final def diff(obtained: T, expected: T): R = diff(DiffInput.Both(obtained, expected))

  /**
    * Attempt to change the configuration of this Differ.
    * If successful, a new differ with the updated configuration will be returned.
    *
    * The configuration change can fail due to
    * - bad "path" that does not match the internal structure of the Differ
    * - The path resolved correctly, but the configuration update operation cannot be applied for that part of the Differ
    *   (e.g. wrong type or wrong operation)
    *
    * @param path The path to traverse to the sub-Differ
    * @param operation The configuration change operation you want to perform on the target sub-Differ
    */
  final def configureRaw(path: ConfigurePath, operation: ConfigureOp): Either[ConfigureError, Differ[T]] = {
    (path.unresolvedSteps, operation) match {
      case (step :: tail, op)                        => configurePath(step, ConfigurePath(path.resolvedSteps :+ step, tail), op)
      case (Nil, ConfigureOp.SetIgnored(newIgnored)) => Right(configureIgnored(newIgnored))
      case (Nil, pairByOp: ConfigureOp.PairBy[_])    => configurePairBy(path, pairByOp)
      case (Nil, op: ConfigureOp.TransformDiffer[_]) => configureTransform(path, op)
    }
  }

  def ignore: Differ[T] = configureIgnored(true)
  def unignore: Differ[T] = configureIgnored(false)

  protected def configureIgnored(newIgnored: Boolean): Differ[T]

  protected def configurePath(step: String, nextPath: ConfigurePath, op: ConfigureOp): Either[ConfigureError, Differ[T]]

  protected def configurePairBy(path: ConfigurePath, op: PairBy[_]): Either[ConfigureError, Differ[T]]

  final private def configureTransform(
    path: ConfigurePath,
    op: ConfigureOp.TransformDiffer[_],
  ): Either[ConfigureError, Differ[T]] = {
    Either.cond(
      op.tag == tag,
      op.unsafeCastFunc[T].apply(this),
      TypeTagMismatch(path = path, obtainedTag = op.tag.tag, expectedTag = tag.tag),
    )
  }
}

object Differ extends DifferTupleInstances with DifferGen {

  def apply[A](implicit differ: Differ[A]): Differ[A] = differ

  def useEquals[T](valueToString: T => String)(implicit tag: LTag[T]): EqualsDiffer[T] =
    new EqualsDiffer[T](isIgnored = false, valueToString = valueToString, tag = tag)

  // TODO: better string diff (edit distance and a description of how to get there?
  //  this can help especially in cases like extra space or special char)
  implicit val stringDiff: ValueDiffer[String] = useEquals[String](str => s""""$str"""")
  implicit val charDiff: ValueDiffer[Char] = useEquals[Char](c => s"'$c'")
  implicit val booleanDiff: ValueDiffer[Boolean] = useEquals[Boolean](_.toString)

  implicit val intDiff: NumericDiffer[Int] = NumericDiffer.make[Int]
  implicit val doubleDiff: NumericDiffer[Double] = NumericDiffer.make[Double]
  implicit val shortDiff: NumericDiffer[Short] = NumericDiffer.make[Short]
  implicit val byteDiff: NumericDiffer[Byte] = NumericDiffer.make[Byte]
  implicit val longDiff: NumericDiffer[Long] = NumericDiffer.make[Long]
  implicit val bigDecimalDiff: NumericDiffer[BigDecimal] = NumericDiffer.make[BigDecimal]
  implicit val bigIntDiff: NumericDiffer[BigInt] = NumericDiffer.make[BigInt]

  implicit def mapDiffer[M[_, _], K, V](
    implicit keyDiffer: ValueDiffer[K],
    valueDiffer: Differ[V],
    tag: LTag[M[K, V]],
    valueTag: LTag[V],
    asMap: MapLike[M],
  ): MapDiffer[M, K, V] = {
    val typeName: TypeName = TypeName.fromLightTypeTag(tag.tag)
    new MapDiffer(
      isIgnored = false,
      keyDiffer = keyDiffer,
      valueDiffer = valueDiffer,
      tag = tag,
      valueTag = valueTag,
      typeName = typeName,
      asMap = asMap,
    )
  }

  implicit def seqDiffer[F[_], A](
    implicit itemDiffer: Differ[A],
    fullTag: LTag[F[A]],
    itemTag: LTag[A],
    asSeq: SeqLike[F],
  ): SeqDiffer[F, A] = {
    val typeName = TypeName.fromLightTypeTag(fullTag.tag)
    SeqDiffer.create(
      itemDiffer = itemDiffer,
      typeName = typeName,
      asSeq = asSeq,
    )
  }

  implicit def setDiffer[F[_], A](
    implicit itemDiffer: Differ[A],
    tag: LTag[F[A]],
    itemTag: LTag[A],
    asSet: SetLike[F],
  ): SetDiffer[F, A] = {
    val typeName = TypeName.fromLightTypeTag(tag.tag)
    SetDiffer.create[F, A](
      itemDiffer = itemDiffer,
      typeName = typeName,
      asSet = asSet,
    )
  }

}

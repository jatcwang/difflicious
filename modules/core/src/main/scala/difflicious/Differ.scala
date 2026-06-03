package difflicious
import difflicious.ConfigureOp.PairBy
import difflicious.differ.*
import difflicious.internal.ConfigureMethods
import difflicious.utils.{TypeName, MapLike, SetLike, SeqLike}

import scala.collection.immutable.ListMap

trait Differ[T] extends ConfigureMethods[T] {
  type R <: DiffResult

  def diff(inputs: DiffInput[T]): R

  final def diff(obtained: T, expected: T): R = diff(DiffInput.Both(obtained, expected))

  /** Attempt to change the configuration of this Differ. If successful, a new differ with the updated configuration
    * will be returned.
    *
    * The configuration change can fail due to
    *   - bad "path" that does not match the internal structure of the Differ
    *   - The path resolved correctly, but the configuration update operation cannot be applied for that part of the
    *     Differ (e.g. wrong type or wrong operation)
    *
    * @param path
    *   The path to traverse to the sub-Differ
    * @param operation
    *   The configuration change operation you want to perform on the target sub-Differ
    */
  final def configureRaw(path: ConfigurePath, operation: ConfigureOp): Either[ConfigureError, Differ[T]] = {
    (path.unresolvedSteps, operation) match {
      case (step :: tail, op) => configurePath(step, ConfigurePath(path.resolvedSteps :+ step, tail), op)
      case (Nil, ConfigureOp.SetIgnored(newIgnored)) => Right(configureIgnored(newIgnored))
      case (Nil, pairByOp: ConfigureOp.PairBy[?]) => configurePairBy(path, pairByOp)
      case (Nil, op: ConfigureOp.TransformDiffer[?]) => Right(configureTransform(op))
    }
  }

  def ignore: Differ[T] = configureIgnored(true)
  def unignore: Differ[T] = configureIgnored(false)

  protected def configureIgnored(newIgnored: Boolean): Differ[T]

  protected def configurePath(step: String, nextPath: ConfigurePath, op: ConfigureOp): Either[ConfigureError, Differ[T]]

  protected def configurePairBy(path: ConfigurePath, op: PairBy[?]): Either[ConfigureError, Differ[T]]

  final private def configureTransform(
    op: ConfigureOp.TransformDiffer[?],
  ): Differ[T] = {
    op.unsafeCastFunc[T].apply(this)
  }
}

object Differ extends DifferTupleInstances with DifferGen with DifferPlatform with DifferTimeInstancesPlatform {

  def apply[A](implicit differ: Differ[A]): Differ[A] = differ

  def oneOf[A](case0: OneOfDiffer.Case[A, ?], otherCases: OneOfDiffer.Case[A, ?]*): OneOfDiffer[A] =
    new OneOfDiffer[A]((case0 +: otherCases).toVector, isIgnored = false, differTypeName = "OneOfDiffer")

  def useEquals[T](valueToString: T => String): EqualsDiffer[T] =
    new EqualsDiffer[T](isIgnored = false, valueToString = valueToString)

  /** A Differ that always return an Ignored result. Useful when you can't really diff something */
  def alwaysIgnore[T]: AlwaysIgnoreDiffer[T] = new AlwaysIgnoreDiffer[T]

  implicit def optionDiffer[T](implicit valueDiffer: Differ[T]): Differ[Option[T]] =
    new OneOfDiffer[Option[T]](
      cases = Vector(
        OneOfDiffer.caseOf[Option[T], Some[T]](
          typeName = recordCaseTypeName("scala.Some", "Some"),
          extract = {
            case value @ Some(_) => Some(value)
            case None => None
          },
          differ = recordDiffer[Some[T]](
            typeName = recordCaseTypeName("scala.Some", "Some"),
            fields = ListMap(
              "value" -> (((value: Some[T]) => value.value), valueDiffer.asInstanceOf[Differ[Any]]),
            ),
          ),
        ),
        OneOfDiffer.caseOf[Option[T], None.type](
          typeName = recordCaseTypeName("scala.None", "None"),
          extract = {
            case None => Some(None)
            case Some(_) => None
          },
          differ = recordDiffer[None.type](
            typeName = recordCaseTypeName("scala.None", "None"),
            fields = ListMap.empty,
          ),
        ),
      ),
      isIgnored = false,
      differTypeName = "OneOfDiffer",
    )

  implicit def eitherDiffer[A, B](implicit
    leftDiffer: Differ[A],
    rightDiffer: Differ[B],
  ): Differ[Either[A, B]] =
    new OneOfDiffer[Either[A, B]](
      cases = Vector(
        OneOfDiffer.caseOf[Either[A, B], Left[A, B]](
          typeName = recordCaseTypeName("scala.util.Left", "Left"),
          extract = {
            case value @ Left(_) => Some(value)
            case Right(_) => None
          },
          differ = recordDiffer[Left[A, B]](
            typeName = recordCaseTypeName("scala.util.Left", "Left"),
            fields = ListMap(
              "value" -> (((value: Left[A, B]) => value.value), leftDiffer.asInstanceOf[Differ[Any]]),
            ),
          ),
        ),
        OneOfDiffer.caseOf[Either[A, B], Right[A, B]](
          typeName = recordCaseTypeName("scala.util.Right", "Right"),
          extract = {
            case value @ Right(_) => Some(value)
            case Left(_) => None
          },
          differ = recordDiffer[Right[A, B]](
            typeName = recordCaseTypeName("scala.util.Right", "Right"),
            fields = ListMap(
              "value" -> (((value: Right[A, B]) => value.value), rightDiffer.asInstanceOf[Differ[Any]]),
            ),
          ),
        ),
      ),
      isIgnored = false,
      differTypeName = "OneOfDiffer",
    )

  implicit val stringDiffer: ValueDiffer[String] = useEquals[String](str => s""""$str"""")
  implicit val charDiffer: ValueDiffer[Char] = useEquals[Char](c => s"'$c'")
  implicit val booleanDiffer: ValueDiffer[Boolean] = useEquals[Boolean](_.toString)

  implicit val intDiffer: NumericDiffer[Int] = NumericDiffer.make[Int](_.toString)
  implicit val shortDiffer: NumericDiffer[Short] = NumericDiffer.make[Short](_.toString)
  implicit val byteDiffer: NumericDiffer[Byte] = NumericDiffer.make[Byte](_.toString)
  implicit val longDiffer: NumericDiffer[Long] = NumericDiffer.make[Long](_.toString)
  implicit val bigDecimalDiffer: NumericDiffer[BigDecimal] = NumericDiffer.make[BigDecimal](_.toString)
  implicit val bigIntDiffer: NumericDiffer[BigInt] = NumericDiffer.make[BigInt](_.toString)

  implicit def mapDiffer[M[_, _], K, V](implicit
    keyDiffer: ValueDiffer[K],
    valueDiffer: Differ[V],
    typeName: TypeName[M[K, V]],
    asMap: MapLike[M],
  ): MapDiffer[M, K, V] = {
    new MapDiffer(
      isIgnored = false,
      keyDiffer = keyDiffer,
      valueDiffer = valueDiffer,
      typeName = typeName,
      asMap = asMap,
    )
  }

  implicit def seqDiffer[F[_], A](implicit
    itemDiffer: Differ[A],
    typeName: TypeName[F[A]],
    asSeq: SeqLike[F],
  ): SeqDiffer[F, A] = {
    SeqDiffer.create(
      itemDiffer = itemDiffer,
      typeName = typeName,
      asSeq = asSeq,
    )
  }

  implicit def setDiffer[F[_], A](implicit
    itemDiffer: Differ[A],
    typeName: TypeName[F[A]],
    asSet: SetLike[F],
  ): SetDiffer[F, A] = {
    SetDiffer.create[F, A](
      itemDiffer = itemDiffer,
      typeName = typeName,
      asSet = asSet,
    )
  }

  private def recordDiffer[A](
    typeName: TypeName.SomeTypeName,
    fields: ListMap[String, (A => Any, Differ[Any])],
  ): RecordDiffer[A] =
    new RecordDiffer[A](
      fieldDiffers = fields,
      isIgnored = false,
      typeName = typeName,
    )

  private def recordCaseTypeName(long: String, short: String): TypeName.SomeTypeName =
    TypeName[Any](
      long = long,
      short = short,
      typeArguments = Nil,
    )

}

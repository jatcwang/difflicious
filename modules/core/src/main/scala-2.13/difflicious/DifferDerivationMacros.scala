package difflicious

import difflicious.utils.{MapLike, SeqLike, SetLike}

import scala.reflect.macros.whitebox

private[difflicious] class DifferDerivationMacros(val c: whitebox.Context)
    extends hearth.MacroCommonsScala2
    with DifferDerivationMacrosCommon {

  import c.universe.{Expr => _, Type => _, TypeName => _, _}

  private type ScalaType = c.universe.Type

  private val seqLikeTypeCtor: ScalaType = weakTypeOf[SeqLike[List]].typeConstructor
  private val setLikeTypeCtor: ScalaType = weakTypeOf[SetLike[Set]].typeConstructor
  private val mapLikeTypeCtor: ScalaType = weakTypeOf[MapLike[Map]].typeConstructor

  def deriveImpl[T: c.WeakTypeTag]: c.Expr[Differ[T]] =
    deriveDifferEntryPoint[T](isCascadeDerive = false)

  def deriveDeepImpl[T: c.WeakTypeTag]: c.Expr[Differ[T]] =
    deriveDifferEntryPoint[T](isCascadeDerive = true)

  def autoImpl[T: c.WeakTypeTag]: c.Expr[Differ[T]] =
    deriveDeepImpl[T]

  protected def staticTypeName[A: Type]: Expr[difflicious.utils.TypeName.SomeTypeName] = {
    val tpt = tq"${Type[A].tpe}"

    c.Expr[difflicious.utils.TypeName.SomeTypeName] {
      q"_root_.difflicious.utils.TypeName[$tpt]"
    }.asInstanceOf[Expr[difflicious.utils.TypeName.SomeTypeName]]
  }

  protected def mkCollectionHelper1[A: Type]: Option[CollectionHelper1[A]] = {
    val appliedType = Type[A].tpe.dealias

    appliedType.typeArgs match {
      case elemTpe :: Nil =>
        val typeCtor = appliedType.typeConstructor
        type Container[X] = A
        val elemType0 = c.WeakTypeTag[Any](elemTpe).asInstanceOf[Type[Any]]

        Some(new CollectionHelper1[A] {
          type F[X] = Container[X]
          type Elem = Any

          implicit val elemType: Type[Elem] = elemType0

          def typeOfSeqLike: Type[SeqLike[F]] =
            UntypedType.toTyped[SeqLike[Container]](appliedTypeOf(seqLikeTypeCtor, typeCtor))

          def typeOfSetLike: Type[SetLike[F]] =
            UntypedType.toTyped[SetLike[Container]](appliedTypeOf(setLikeTypeCtor, typeCtor))
        })

      case _ => None
    }
  }

  protected def mkCollectionHelper2[A: Type]: Option[CollectionHelper2[A]] = {
    val appliedType = Type[A].tpe.dealias

    appliedType.typeArgs match {
      case keyTpe :: valueTpe :: Nil =>
        val typeCtor = appliedType.typeConstructor
        type Container[X, Y] = A
        val keyType0 = c.WeakTypeTag[Any](keyTpe).asInstanceOf[Type[Any]]
        val valueType0 = c.WeakTypeTag[Any](valueTpe).asInstanceOf[Type[Any]]

        Some(new CollectionHelper2[A] {
          type F[X, Y] = Container[X, Y]
          type Key = Any
          type Value = Any

          implicit val keyType: Type[Key] =
            keyType0
          implicit val valueType: Type[Value] =
            valueType0

          def typeOfMapLike: Type[MapLike[F]] =
            UntypedType.toTyped[MapLike[Container]](appliedTypeOf(mapLikeTypeCtor, typeCtor))
        })

      case _ => None
    }
  }

  private def appliedTypeOf(typeCtor: ScalaType, typeArgument: ScalaType): ScalaType =
    c.universe.appliedType(typeCtor, List(typeArgument))
}

private[difflicious] class DifferMacros(override val c: whitebox.Context) extends DifferDerivationMacros(c)

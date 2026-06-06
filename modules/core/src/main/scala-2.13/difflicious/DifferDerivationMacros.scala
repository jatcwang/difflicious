package difflicious

import difflicious.differ.ValueDiffer
import difflicious.utils.{MapLike, SeqLike, SetLike}

import scala.reflect.macros.whitebox

private[difflicious] class DifferDerivationMacros(val c: whitebox.Context)
    extends hearth.MacroCommonsScala2
    with DifferDerivationMacrosCommon {

  import c.universe.{Expr => _, Type => _, TypeName => _, _}

  private type ScalaType = c.universe.Type
  private type ScalaTree = c.universe.Tree

  private val optionTypeCtor: ScalaType = weakTypeOf[Option[Any]].typeConstructor
  private val eitherTypeCtor: ScalaType = weakTypeOf[Either[Any, Any]].typeConstructor
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

  protected def decomposeApplied1[A: Type]: Option[Applied1[A]] = {
    val appliedType = Type[A].tpe.dealias

    appliedType.typeArgs match {
      case elemTpe :: Nil =>
        val typeCtor = appliedType.typeConstructor

        Some(new Applied1[A] {
          type Elem = Any

          implicit val elemType: Type[Elem] =
            c.WeakTypeTag[Elem](elemTpe).asInstanceOf[Type[Elem]]

          private lazy val seqLike = inferImplicit(appliedTypeOf(seqLikeTypeCtor, typeCtor))
          private lazy val setLike = inferImplicit(appliedTypeOf(setLikeTypeCtor, typeCtor))

          def isOptionLike: Boolean =
            appliedType <:< c.universe.appliedType(optionTypeCtor, List(elemTpe))

          def hasSeqLike: Boolean = seqLike.isDefined

          def hasSetLike: Boolean = setLike.isDefined

          def buildSeqDiffer(itemDiffer: Expr[Differ[Elem]]): Expr[Differ[A]] = {
            val fTpt = tq"$typeCtor"
            val elemTpt = tq"$elemTpe"
            val aTpt = tq"$appliedType"
            val typeName = staticTypeName[A]
            val seqLikeTree = seqLike.get

            c.Expr[Differ[A]] {
              q"""
                _root_.difflicious.differ.SeqDiffer.create[$fTpt, $elemTpt](
                  itemDiffer = ${itemDiffer.tree},
                  typeName = ${typeName.tree},
                  asSeq = $seqLikeTree
                ).asInstanceOf[_root_.difflicious.Differ[$aTpt]]
              """
            }
          }

          def buildSetDiffer(itemDiffer: Expr[Differ[Elem]]): Expr[Differ[A]] = {
            val fTpt = tq"$typeCtor"
            val elemTpt = tq"$elemTpe"
            val aTpt = tq"$appliedType"
            val typeName = staticTypeName[A]
            val setLikeTree = setLike.get

            c.Expr[Differ[A]] {
              q"""
                _root_.difflicious.differ.SetDiffer.create[$fTpt, $elemTpt](
                  itemDiffer = ${itemDiffer.tree},
                  typeName = ${typeName.tree},
                  asSet = $setLikeTree
                ).asInstanceOf[_root_.difflicious.Differ[$aTpt]]
              """
            }
          }
        })

      case _ => None
    }
  }

  protected def decomposeApplied2[A: Type]: Option[Applied2[A]] = {
    val appliedType = Type[A].tpe.dealias

    appliedType.typeArgs match {
      case keyTpe :: valueTpe :: Nil =>
        val typeCtor = appliedType.typeConstructor

        Some(new Applied2[A] {
          type Key = Any
          type Value = Any

          implicit val keyType: Type[Key] =
            c.WeakTypeTag[Key](keyTpe).asInstanceOf[Type[Key]]
          implicit val valueType: Type[Value] =
            c.WeakTypeTag[Value](valueTpe).asInstanceOf[Type[Value]]

          private lazy val mapLike = inferImplicit(appliedTypeOf(mapLikeTypeCtor, typeCtor))

          def isEitherLike: Boolean =
            appliedType <:< c.universe.appliedType(eitherTypeCtor, List(keyTpe, valueTpe))

          def hasMapLike: Boolean = mapLike.isDefined

          def buildMapDiffer(
            keyDiffer: Expr[ValueDiffer[Key]],
            valueDiffer: Expr[Differ[Value]],
          ): Expr[Differ[A]] = {
            val mTpt = tq"$typeCtor"
            val keyTpt = tq"$keyTpe"
            val valueTpt = tq"$valueTpe"
            val aTpt = tq"$appliedType"
            val typeName = staticTypeName[A]
            val mapLikeTree = mapLike.get

            c.Expr[Differ[A]] {
              q"""
                new _root_.difflicious.differ.MapDiffer[$mTpt, $keyTpt, $valueTpt](
                  isIgnored = false,
                  keyDiffer = ${keyDiffer.tree},
                  valueDiffer = ${valueDiffer.tree},
                  typeName = ${typeName.tree},
                  asMap = $mapLikeTree
                ).asInstanceOf[_root_.difflicious.Differ[$aTpt]]
              """
            }
          }
        })

      case _ => None
    }
  }

  private def appliedTypeOf(typeCtor: ScalaType, typeArgument: ScalaType): ScalaType =
    c.universe.appliedType(typeCtor, List(typeArgument))

  private def inferImplicit(tpe: ScalaType): Option[ScalaTree] =
    try {
      val inferred = c.inferImplicitValue(tpe, silent = true, withMacrosDisabled = false)
      if (inferred == EmptyTree) None else Some(inferred)
    } catch {
      case _: c.TypecheckException => None
    }
}

private[difflicious] class DifferMacros(override val c: whitebox.Context) extends DifferDerivationMacros(c)

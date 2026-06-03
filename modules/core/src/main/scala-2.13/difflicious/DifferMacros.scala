package difflicious

import difflicious.differ.OneOfDiffer
import difflicious.utils.TypeName

import scala.reflect.macros.whitebox

private[difflicious] class DifferMacros(val c: whitebox.Context)
    extends hearth.MacroCommonsScala2
    with DifferDerivationMacros {

  def deriveImpl[T: c.WeakTypeTag]: c.Expr[Differ[T]] =
    deriveDiffer[T](deriveIfMissing = false)

  def deriveDeepImpl[T: c.WeakTypeTag]: c.Expr[Differ[T]] =
    deriveDiffer[T](deriveIfMissing = true)

  def autoImpl[T: c.WeakTypeTag]: c.Expr[Differ[T]] =
    deriveDeepImpl[T]

  def summonOrDeriveImpl[T: c.WeakTypeTag]: c.Expr[Differ[T]] = {
    import c.universe.{Expr => _, _}

    val tpe = weakTypeOf[T]
    val differTpe = c.universe.appliedType(typeOf[Differ[_]].typeConstructor, List(tpe))
    val inferred = c.inferImplicitValue(differTpe, silent = true)

    if (inferred == EmptyTree) deriveDeepImpl[T]
    else c.Expr[Differ[T]](inferred)
  }

  override protected def subtypeCase[A: Type, B: Type](
    typeName: Expr[TypeName.SomeTypeName],
    differ: Expr[Differ[B]],
  ): Expr[OneOfDiffer.Case[A, Any]] = {
    import c.universe.{Expr => _, _}

    val aType = Type[A].asUntyped.asInstanceOf[c.Type]
    val bType = Type[B].asUntyped.asInstanceOf[c.Type]
    val typeNameTree = typeName.asUntyped.asInstanceOf[c.Tree]
    val differTree = differ.asUntyped.asInstanceOf[c.Tree]

    c.Expr[OneOfDiffer.Case[A, Any]] {
      q"""
        {
          val runtimeClass = _root_.scala.Predef.classOf[$bType]
          difflicious.differ.OneOfDiffer.Case[$aType, Any](
            typeName = $typeNameTree,
            extract = (value: Any) =>
              if (runtimeClass.isInstance(value)) _root_.scala.Some(value)
              else _root_.scala.None,
            differ = $differTree.asInstanceOf[difflicious.Differ[Any]]
          )
        }
      """
    }.asInstanceOf[Expr[OneOfDiffer.Case[A, Any]]]
  }

  override protected def singletonSubtypeCase[A: Type, B: Type](
    typeName: Expr[TypeName.SomeTypeName],
    differ: Expr[Differ[B]],
    singleton: Expr[B],
  ): Expr[OneOfDiffer.Case[A, Any]] = {
    import c.universe.{Expr => _, _}

    val aType = Type[A].asUntyped.asInstanceOf[c.Type]
    val typeNameTree = typeName.asUntyped.asInstanceOf[c.Tree]
    val differTree = differ.asUntyped.asInstanceOf[c.Tree]
    val singletonTree = singleton.asUntyped.asInstanceOf[c.Tree]

    c.Expr[OneOfDiffer.Case[A, Any]] {
      q"""
        difflicious.differ.OneOfDiffer.Case[$aType, Any](
          typeName = $typeNameTree,
          extract = (value: Any) =>
            if (value == $singletonTree) _root_.scala.Some(value)
            else _root_.scala.None,
          differ = $differTree.asInstanceOf[difflicious.Differ[Any]]
        )
      """
    }.asInstanceOf[Expr[OneOfDiffer.Case[A, Any]]]
  }

  override protected def appliedType1[A: Type]: Option[AppliedType1] = {
    val dealiased = Type[A].asUntyped.asInstanceOf[c.Type].dealias
    dealiased.typeArgs match {
      case itemType :: Nil =>
        Some(new AppliedType1(dealiased.typeConstructor.asInstanceOf[UntypedType], itemType.as_??))
      case _ =>
        None
    }
  }

  override protected def appliedType2[A: Type]: Option[AppliedType2] = {
    val dealiased = Type[A].asUntyped.asInstanceOf[c.Type].dealias
    dealiased.typeArgs match {
      case keyType :: valueType :: Nil =>
        Some(
          new AppliedType2(
            dealiased.typeConstructor.asInstanceOf[UntypedType],
            keyType.as_??,
            valueType.as_??,
          ),
        )
      case _ =>
        None
    }
  }

  override protected def appliedType(typeConstructor: UntypedType, typeArguments: UntypedType*): UntypedType = {
    val base = typeConstructor.asInstanceOf[c.Type].dealias
    val typeConstructorType =
      if (base.typeArgs.isEmpty) base
      else base.typeConstructor

    c.universe
      .appliedType(
        typeConstructorType,
        typeArguments.toList.map(_.asInstanceOf[c.Type]),
      )
      .asInstanceOf[UntypedType]
  }
}

package difflicious

import difflicious.differ.OneOfDiffer
import difflicious.utils.TypeName

import scala.quoted.{Expr as QExpr, Quotes, Type as QType}

private[difflicious] class DifferMacros(q: Quotes) extends hearth.MacroCommonsScala3(using q), DifferDerivationMacros {

  private given Quotes = q

  override protected def subtypeCase[A: QType, B: QType](
    typeName: QExpr[TypeName.SomeTypeName],
    differ: QExpr[Differ[B]],
  ): QExpr[OneOfDiffer.Case[A, Any]] = {
    val classTag = QExpr.summon[scala.reflect.ClassTag[B]].getOrElse {
      q.reflect.report.errorAndAbort(
        s"Cannot derive difflicious.Differ[${q.reflect.TypeRepr.of[A].show}]: could not materialize ClassTag for ${q.reflect.TypeRepr.of[B].show}",
      )
    }

    '{
      difflicious.differ.OneOfDiffer
        .Case[Any, Any](
          typeName = ${ typeName },
          extract = {
            val runtimeClass = ${ classTag }.runtimeClass
            (value: Any) =>
              if (runtimeClass.isInstance(value)) Some(value)
              else None
          },
          differ = ${ differ }.asInstanceOf[difflicious.Differ[Any]],
        )
        .asInstanceOf[difflicious.differ.OneOfDiffer.Case[A, Any]]
    }
  }

  override protected def singletonSubtypeCase[A: QType, B: QType](
    typeName: QExpr[TypeName.SomeTypeName],
    differ: QExpr[Differ[B]],
    singleton: QExpr[B],
  ): QExpr[OneOfDiffer.Case[A, Any]] =
    '{
      difflicious.differ.OneOfDiffer
        .Case[Any, Any](
          typeName = ${ typeName },
          extract = (value: Any) =>
            if (value == ${ singleton }) Some(value)
            else None,
          differ = ${ differ }.asInstanceOf[difflicious.Differ[Any]],
        )
        .asInstanceOf[difflicious.differ.OneOfDiffer.Case[A, Any]]
    }

  override protected def appliedType1[A: QType]: Option[AppliedType1] = {
    import quotes.reflect.*

    TypeRepr.of[A].dealias match {
      case AppliedType(typeConstructor, List(itemType)) =>
        Some(new AppliedType1(typeConstructor, itemType.as_??))
      case _ =>
        None
    }
  }

  override protected def appliedType2[A: QType]: Option[AppliedType2] = {
    import quotes.reflect.*

    TypeRepr.of[A].dealias match {
      case AppliedType(typeConstructor, List(keyType, valueType)) =>
        Some(new AppliedType2(typeConstructor, keyType.as_??, valueType.as_??))
      case _ =>
        None
    }
  }

  override protected def appliedType(
    appliedType: UntypedType,
    typeArguments: UntypedType*,
  ): UntypedType = {
    import quotes.reflect.*

    appliedType.asInstanceOf[TypeRepr].dealias match {
      case AppliedType(typeConstructor, _) =>
        AppliedType(typeConstructor, typeArguments.toList.map(_.asInstanceOf[TypeRepr]))
      case other =>
        AppliedType(other, typeArguments.toList.map(_.asInstanceOf[TypeRepr]))
    }
  }
}

private[difflicious] object DifferMacros {

  def deriveImpl[T: QType](using q: Quotes): QExpr[Differ[T]] =
    new DifferMacros(q).deriveDiffer[T](deriveIfMissing = false)

  def deriveDeepImpl[T: QType](using q: Quotes): QExpr[Differ[T]] =
    new DifferMacros(q).deriveDiffer[T](deriveIfMissing = true)

  def deriveAutoImpl[T: QType](using q: Quotes): QExpr[Differ[T]] = {
    import q.reflect.report

    new DifferMacros(q).deriveAutoDiffer[T](
      reportError = message => report.error(message),
      fallback = '{ scala.Predef.??? : Differ[T] },
    )
  }
}

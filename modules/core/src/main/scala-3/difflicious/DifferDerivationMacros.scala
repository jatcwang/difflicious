package difflicious

import difflicious.utils.{MapLike, SeqLike, SetLike}

import scala.quoted.{Expr as QExpr, Quotes, Type as QType}

private[difflicious] class DifferDerivationMacros(q: Quotes)
    extends hearth.MacroCommonsScala3(using q),
      DifferDerivationMacrosCommon {

  private given Quotes = q

  private def typeConstructorOf[A: QType]: q.reflect.TypeRepr = {
    import q.reflect.*

    TypeRepr.of[A] match {
      case AppliedType(typeCtor, _) => typeCtor
      case other => other
    }
  }

  private def seqLikeTypeCtor: q.reflect.TypeRepr =
    typeConstructorOf[SeqLike[List]]

  private def setLikeTypeCtor: q.reflect.TypeRepr =
    typeConstructorOf[SetLike[Set]]

  private def mapLikeTypeCtor: q.reflect.TypeRepr =
    typeConstructorOf[MapLike[Map]]

  private def appliedTypeOf(typeCtor: q.reflect.TypeRepr, typeArg: q.reflect.TypeRepr): q.reflect.TypeRepr =
    typeCtor.appliedTo(List(typeArg))

  private def typeOf[A](tpe: q.reflect.TypeRepr): Type[A] =
    UntypedType.toTyped[A](tpe.asInstanceOf[UntypedType])

  protected def staticTypeName[A: Type]: Expr[difflicious.utils.TypeName.SomeTypeName] = {
    '{
      difflicious.utils.TypeName[A]
    }
      .asInstanceOf[Expr[difflicious.utils.TypeName.SomeTypeName]]
  }

  protected def mkCollectionHelper1[A: Type]: Option[CollectionHelper1[A]] = {
    import q.reflect.*

    TypeRepr.of[A].dealias match {
      case AppliedType(typeCtor, List(elemTpe)) =>
        elemTpe.asType match {
          case '[elem] =>
            val elemQType = QType.of[elem]
            type Container[X] = A

            Some(new CollectionHelper1[A] {
              type F[X] = Container[X]
              type Elem = elem

              implicit val elemType: Type[Elem] =
                elemQType.asInstanceOf[Type[elem]]

              def typeOfSeqLike: Type[SeqLike[F]] =
                typeOf[SeqLike[Container]](appliedTypeOf(seqLikeTypeCtor, typeCtor))

              def typeOfSetLike: Type[SetLike[F]] =
                typeOf[SetLike[Container]](appliedTypeOf(setLikeTypeCtor, typeCtor))
            })
          case _ => None
        }
      case _ => None
    }
  }

  protected def mkCollectionHelper2[A: Type]: Option[CollectionHelper2[A]] = {
    import q.reflect.*

    TypeRepr.of[A].dealias match {
      case AppliedType(typeCtor, List(keyTpe, valueTpe)) =>
        (keyTpe.asType, valueTpe.asType) match {
          case ('[key], '[value]) =>
            val keyQType = QType.of[key]
            val valueQType = QType.of[value]
            type Container[X, Y] = A

            Some(new CollectionHelper2[A] {
              type F[X, Y] = Container[X, Y]
              type Key = key
              type Value = value

              implicit val keyType: Type[Key] =
                keyQType.asInstanceOf[Type[key]]
              implicit val valueType: Type[Value] =
                valueQType.asInstanceOf[Type[value]]

              def typeOfMapLike: Type[MapLike[F]] =
                typeOf[MapLike[Container]](appliedTypeOf(mapLikeTypeCtor, typeCtor))
            })
          case _ => None
        }
      case _ => None
    }
  }

}

private[difflicious] object DifferDerivationMacros {

  def deriveImpl[T: QType](using q: Quotes): QExpr[Differ[T]] =
    new DifferDerivationMacros(q).deriveDifferEntryPoint[T](isCascadeDerive = false)

  def deriveDeepImpl[T: QType](using q: Quotes): QExpr[Differ[T]] =
    new DifferDerivationMacros(q).deriveDifferEntryPoint[T](isCascadeDerive = true)
}

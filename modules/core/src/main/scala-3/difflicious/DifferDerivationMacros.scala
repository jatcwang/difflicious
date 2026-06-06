package difflicious

import difflicious.differ.ValueDiffer
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

  private def inferImplicit(tpe: q.reflect.TypeRepr): Option[q.reflect.Term] = {
    import q.reflect.*

    Implicits.search(tpe) match {
      case success: ImplicitSearchSuccess => Some(success.tree)
      case _: ImplicitSearchFailure => None
      case _ => None
    }
  }

  protected def decomposeApplied1[A: Type]: Option[Applied1[A]] = {
    import q.reflect.*

    TypeRepr.of[A].dealias match {
      case AppliedType(typeCtor, List(elemTpe)) =>
        elemTpe.asType match {
          case '[elem] =>
            val elemQType = QType.of[elem]
            val typeName = staticTypeName[A]
            val optionLike = TypeRepr.of[A] <:< TypeRepr.of[Option[elem]]

            Some(new Applied1[A] {
              type Elem = elem

              implicit val elemType: Type[Elem] =
                elemQType.asInstanceOf[Type[Elem]]

              private lazy val seqLike =
                inferImplicit(appliedTypeOf(seqLikeTypeCtor, typeCtor))

              private lazy val setLike =
                inferImplicit(appliedTypeOf(setLikeTypeCtor, typeCtor))

              def isOptionLike: Boolean =
                optionLike

              def hasSeqLike: Boolean = seqLike.isDefined

              def hasSetLike: Boolean = setLike.isDefined

              def buildSeqDiffer(itemDiffer: Expr[Differ[Elem]]): Expr[Differ[A]] = {
                val call =
                  Select
                    .unique(Ref(Symbol.requiredModule("difflicious.differ.SeqDiffer")), "create")
                    .appliedToTypes(List(typeCtor, elemTpe))
                    .appliedToArgs(List(itemDiffer.asTerm, typeName.asTerm, seqLike.get))

                call.asExpr.asInstanceOf[Expr[Differ[A]]]
              }

              def buildSetDiffer(itemDiffer: Expr[Differ[Elem]]): Expr[Differ[A]] = {
                val call =
                  Select
                    .unique(Ref(Symbol.requiredModule("difflicious.differ.SetDiffer")), "create")
                    .appliedToTypes(List(typeCtor, elemTpe))
                    .appliedToArgs(List(itemDiffer.asTerm, typeName.asTerm, setLike.get))

                call.asExpr.asInstanceOf[Expr[Differ[A]]]
              }
            })
          case _ => None
        }
      case _ => None
    }
  }

  protected def decomposeApplied2[A: Type]: Option[Applied2[A]] = {
    import q.reflect.*

    TypeRepr.of[A].dealias match {
      case AppliedType(typeCtor, List(keyTpe, valueTpe)) =>
        (keyTpe.asType, valueTpe.asType) match {
          case ('[key], '[value]) =>
            val keyQType = QType.of[key]
            val valueQType = QType.of[value]
            val typeName = staticTypeName[A]
            val eitherLike = TypeRepr.of[A] <:< TypeRepr.of[Either[key, value]]

            Some(new Applied2[A] {
              type Key = key
              type Value = value

              implicit val keyType: Type[Key] =
                keyQType.asInstanceOf[Type[Key]]
              implicit val valueType: Type[Value] =
                valueQType.asInstanceOf[Type[Value]]

              private lazy val mapLike =
                inferImplicit(appliedTypeOf(mapLikeTypeCtor, typeCtor))

              def isEitherLike: Boolean =
                eitherLike

              def hasMapLike: Boolean = mapLike.isDefined

              def buildMapDiffer(
                keyDiffer: Expr[ValueDiffer[Key]],
                valueDiffer: Expr[Differ[Value]],
              ): Expr[Differ[A]] = {
                val mapDifferClass = Symbol.requiredClass("difflicious.differ.MapDiffer")

                val call =
                  New(TypeIdent(mapDifferClass))
                    .select(mapDifferClass.primaryConstructor)
                    .appliedToTypes(List(typeCtor, keyTpe, valueTpe))
                    .appliedToArgs(
                      List(
                        Expr(false).asTerm,
                        keyDiffer.asTerm,
                        valueDiffer.asTerm,
                        typeName.asTerm,
                        mapLike.get,
                      ),
                    )

                call.asExpr.asInstanceOf[Expr[Differ[A]]]
              }
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

  def deriveAutoImpl[T: QType](using q: Quotes): QExpr[Differ[T]] = {
    import q.reflect.report

    new DifferDerivationMacros(q).deriveAutoDiffer[T](
      reportError = message => report.error(message),
      fallback = '{ scala.Predef.??? : Differ[T] },
    )
  }
}

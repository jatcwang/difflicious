package difflicious.internal
import difflicious.{ConfigurePath, Differ, ConfigureOp}

import scala.quoted.*
import scala.annotation.tailrec
import difflicious.internal.ConfigureMethodImpls.*

trait ConfigureMethods[T]:
  this: Differ[T] =>

  val requiredShapeMsg = "Configure path must have shape like: _.field1.each.field2.subType[ASubClass]"

  inline def ignoreAt[U](inline path: T => U): Differ[T] =
    ${ ignoreAt_impl('this, 'path) }

  inline def configure[U](inline path: T => U)(configFunc: Differ[U] => Differ[U]): Differ[T] =
    ${ configure_impl('this, 'path, 'configFunc) }

  inline def replace[U](inline path: T => U)(newDiffer: Differ[U]): Differ[T] =
    ${ replace_impl('this, 'path, 'newDiffer) }

private[difflicious] object ConfigureMethodImpls:

  def ignoreAt_impl[T: Type, U](differ: Expr[Differ[T]], path: Expr[T => U])(using Quotes): Expr[Differ[T]] = {
    '{
      (${ differ }
        .configureRaw(ConfigurePath.fromPath(${ collectPathElements(path) }), ConfigureOp.ignore)) match {
        case Right(d) => d
        case Left(e) => throw e
      }
    }
  }

  def configure_impl[T: Type, U: Type](
    differ: Expr[Differ[T]],
    path: Expr[T => U],
    configFunc: Expr[Differ[U] => Differ[U]],
  )(using
    Quotes,
  ): Expr[Differ[T]] =
    '{
      (${ differ }
        .configureRaw(
          ConfigurePath.fromPath(${ collectPathElements(path) }),
          ConfigureOp.TransformDiffer(${ configFunc }),
        )) match {
        case Right(d) => d
        case Left(e) => throw e
      }
    }

  def replace_impl[T: Type, U: Type](
    differ: Expr[Differ[T]],
    path: Expr[T => U],
    newDiffer: Expr[Differ[U]],
  )(using
    Quotes,
  ): Expr[Differ[T]] =
    '{
      ${ differ }
        .configureRaw(
          ConfigurePath.fromPath(${ collectPathElements(path) }),
          ConfigureOp.TransformDiffer[U](_ => ${ newDiffer }),
        ) match {
        case Right(d) => d
        case Left(e) => throw e
      }
    }

  def collectPathElements[T, U](pathExpr: Expr[T => U])(using Quotes): Expr[List[String]] = {
    import quotes.reflect.*

    def isImplicitConversion(term: Term): Boolean =
      term.symbol.flags.is(Flags.Implicit)

    def relationshipPathElement(superType: TypeRepr, subType: TypeRepr): Option[Expr[String]] =
      (superType.asType, subType.asType) match {
        case ('[a], '[b]) =>
          Expr.summon[difflicious.DifferSubTypeRelationship[a, b]].map { relationship =>
            '{ $relationship.path }
          }
        case _ => None
      }

    @tailrec
    def collectPathElementsLoop(pathAccum: List[Expr[String]], cur: Term): List[Expr[String]] =
      cur match {
        case Select(rest, name) =>
          collectPathElementsLoop(Expr(name.toString) :: pathAccum, rest)
        case Apply(
              TypeApply(Select(Apply(TypeApply(_, _ :: Nil), rest :: Nil), "subType"), _ :: Nil),
              relationship :: Nil,
            ) =>
          val relationshipExpr = relationship.asExprOf[difflicious.DifferSubTypeRelationship[?, ?]]
          collectPathElementsLoop('{ $relationshipExpr.path } :: pathAccum, rest)
        case x @ TypeApply(Select(Apply(TypeApply(_, _ :: Nil), rest :: Nil), "subType"), subType :: Nil) =>
          val superTypeRepr = rest.tpe.widen.dealias
          val subTypeRepr = subType.tpe.dealias
          val typeSym = subTypeRepr.typeSymbol
          if subTypeRepr <:< superTypeRepr then
            if superTypeRepr.typeSymbol.children.contains(typeSym) then
              collectPathElementsLoop(Expr(typeSym.name) :: pathAccum, rest)
            else
              report.error(
                s"subType requires that the super type be a sealed trait (enum), and the subtype being a direct children of the super type.",
                x.asExpr,
              )
              List.empty
          else
            relationshipPathElement(superTypeRepr, subTypeRepr) match {
              case Some(pathElement) => collectPathElementsLoop(pathElement :: pathAccum, rest)
              case None =>
                report.error(
                  s"subType requires either a true subtype or an implicit DifferSubTypeRelationship.",
                  x.asExpr,
                )
                List.empty
            }
        case Apply(Apply(conversion, rest :: Nil), _) if isImplicitConversion(conversion) =>
          collectPathElementsLoop(pathAccum, rest)
        case Apply(conversion, rest :: Nil) if isImplicitConversion(conversion) =>
          collectPathElementsLoop(pathAccum, rest)
        case Ident(_) => pathAccum
        case _ => {
          throw new Exception(cur.show(using Printer.TreeShortCode) ++ "|||" ++ cur.show(using Printer.TreeStructure))
        }
      }

    pathExpr.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(tree))), _)) =>
        Expr.ofList(collectPathElementsLoop(List.empty, tree))
      case _ =>
        report.error(s"Unexpected path expression. This is a bug: ${pathExpr.asTerm.show(using Printer.TreeStructure)}")
        '{ ??? }
    }
  }

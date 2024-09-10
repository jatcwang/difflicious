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
        case Left(e)  => throw e
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
        case Left(e)  => throw e
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
        case Left(e)  => throw e
      }
    }

  def collectPathElements[T, U](pathExpr: Expr[T => U])(using Quotes): Expr[List[String]] = {
    import quotes.reflect.*

//    import dotty.tools.dotc.ast.Trees._
    @tailrec
    def collectPathElementsLoop(pathAccum: List[String], cur: Term): List[String] =
      cur match {
        case Select(rest, name) =>
          collectPathElementsLoop(name.toString :: pathAccum, rest)
        case x @ TypeApply(Select(Apply(TypeApply(_, superType :: Nil), rest :: Nil), "subType"), subType :: Nil) =>
          val typeSym = subType.tpe.dealias.typeSymbol
          if superType.symbol.children.contains(subType.tpe.dealias.typeSymbol) then
            collectPathElementsLoop(typeSym.name :: pathAccum, rest)
          else
            report.error(
              s"subType requires that the super type be a sealed trait (enum), and the subtype being a direct children of the super type.",
              x.asExpr,
            )
            List.empty
        case Apply(Apply(TypeApply(Ident(name), _), rest :: Nil), _) if name.toString == "toEachableOps" =>
          collectPathElementsLoop(pathAccum, rest)
        case Ident(_) => pathAccum
        case _ => {
          throw new Exception(cur.show(using Printer.TreeShortCode) ++ "|||" ++ cur.show(using Printer.TreeStructure))
        }
      }

    pathExpr.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(tree))), _)) =>
        Expr(collectPathElementsLoop(List.empty, tree))
      case _ =>
        report.error(s"Unexpected path expression. This is a bug: ${pathExpr.asTerm.show(using Printer.TreeStructure)}")
        '{ ??? }
    }
  }

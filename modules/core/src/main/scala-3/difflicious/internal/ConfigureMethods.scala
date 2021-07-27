package difflicious.internal
import difflicious.{ConfigurePath, Differ, ConfigureOp}

import scala.annotation.nowarn
import scala.quoted.*
import scala.annotation.tailrec
import difflicious.internal.ConfigureMethodImpls._
import difflicious.internal.EitherGetSyntax._

trait ConfigureMethods[T]:
  this: Differ[T] =>

  val requiredShapeMsg = "Configure path must have shape like: _.field1.each.field2.subType[ASubClass]"

  inline def ignoreAt[U](inline path: T => U): Differ[T] =
    ${ ignoreAt_impl('this, 'path) }

  inline def configure[U](path: T => U)(configFunc: Differ[U] => Differ[U]): Differ[T] =
    ${ configure_impl('this, 'path, 'configFunc) }

  inline def replace[U](path: T => U)(newDiffer: Differ[U]): Differ[T] =
    ${ replace_impl('this, 'path, 'newDiffer) }

private[difflicious] object ConfigureMethodImpls:

  def ignoreAt_impl[T: Type, U](differ: Expr[Differ[T]], path: Expr[T => U])(using Quotes): Expr[Differ[T]] = {
    '{
      ${ differ }
        .configureRaw(ConfigurePath.fromPath(${ collectPathElements(path) }), ConfigureOp.ignore)
        .unsafeGet
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
      ${ differ }
        .configureRaw(
          ConfigurePath.fromPath(${ collectPathElements(path) }),
          ConfigureOp.TransformDiffer(${ configFunc }),
        )
        .unsafeGet
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
        )
        .unsafeGet
    }

  def collectPathElements[T, U](pathExpr: Expr[T => U])(using Quotes): Expr[List[String]] = {
    import quotes.reflect.*

    @tailrec
    def goCollect(pathAccum: List[String], cur: Term): List[String] =
      // FIXME:
      cur match {
        case Select(rest, name) =>
          goCollect(name.toString :: pathAccum, rest)
        case TypeApply(Select(Apply(_, rest :: Nil), "subType"), TypeSelect(_, subTypeName) :: Nil) =>
          // FIXME: need to check is sealed trait subtype
          goCollect(subTypeName.toString :: pathAccum, rest)
        case Apply(Apply(TypeApply(Ident(name), _), rest :: Nil), _) if name.toString == "toEachableOps" =>
          goCollect(pathAccum, rest)
        case Ident(_) => pathAccum
        case _ => {
          throw new Exception("FIXME handle other cases than Select")
        }
      }

    pathExpr.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(Select(rest, name)))), _)) =>
        Expr(goCollect(List(name.toString), rest))
      case _ =>
        println(pathExpr.asTerm.show(using Printer.TreeStructure))
        Expr(List(pathExpr.asTerm.show(using Printer.TreeStructure)))
    }
  }

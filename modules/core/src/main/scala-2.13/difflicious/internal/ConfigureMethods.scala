package difflicious.internal

import difflicious.ConfigureOp.PairBy
import difflicious.internal.EitherGetSyntax.EitherExtensionOps
import difflicious.{ConfigurePath, Differ}
import difflicious.utils.Pairable
import izumi.reflect.macrortti.LTag

import scala.collection.mutable
import scala.annotation.{tailrec, nowarn}
import scala.reflect.macros.blackbox

trait ConfigureMethods[T] { this: Differ[T] =>

  def ignoreAt[U](path: T => U): Differ[T] = macro ConfigureMacro.ignoreAt_impl[T, U]

  def configure[U](path: T => U)(configFunc: Differ[U] => Differ[U])(implicit tag: LTag[U]): Differ[T] =
    macro ConfigureMacro.configure_impl[T, U]

  def replace[U](path: T => U)(newDiffer: Differ[U])(implicit tag: LTag[U]): Differ[T] =
    macro ConfigureMacro.replace_impl[T, U]
}

// pairBy has to be defined differently for better type inference.
final class PairByOps[F[_], A](differ: Differ[F[A]]) {
  def pairBy[B](f: A => B)(implicit aTag: LTag[A]): Differ[F[A]] =
    differ.configureRaw(ConfigurePath.current, PairBy.ByFunc(f, aTag)).unsafeGet

  def pairByIndex: Differ[F[A]] =
    differ.configureRaw(ConfigurePath.current, PairBy.Index).unsafeGet
}

trait ToPairByOps {
  @nowarn("msg=.*never used.*")
  implicit def toPairByOps[F[_]: Pairable, A](differ: Differ[F[A]]): PairByOps[F, A] = new PairByOps(differ)
}

// Implementation inspired by quicklen's path macro.
// See https://github.com/softwaremill/quicklens/blob/c2fd335b80f3d4d55a76d146d8308d95575dd749/quicklens/src/main/scala-2/com/softwaremill/quicklens/QuicklensMacros.scala
object ConfigureMacro {

  val requiredShapeMsg = "Configure path must have shape like: _.field1.each.field2.subType[ASubClass]"

  def configure_impl[T, U](c: blackbox.Context)(
    path: c.Expr[T => U],
  )(
    configFunc: c.Expr[Differ[U] => Differ[U]],
  )(tag: c.Expr[LTag[U]]): c.Tree = {
    import c.universe._
    val opTree = q"_root_.difflicious.ConfigureOp.TransformDiffer($configFunc, $tag)"
    toConfigureRawCall(c)(path, opTree)
  }

  def replace_impl[T, U](c: blackbox.Context)(
    path: c.Expr[T => U],
  )(
    newDiffer: c.Expr[Differ[U]],
  )(tag: c.Expr[LTag[U]])(implicit uTypeTag: c.WeakTypeTag[U]): c.Tree = {
    import c.universe._
    val opTree =
      q"_root_.difflicious.ConfigureOp.TransformDiffer[${uTypeTag.tpe}](unused => { val _ = unused; $newDiffer }, $tag)"
    toConfigureRawCall(c)(path, opTree)
  }

  def ignoreAt_impl[T: c.WeakTypeTag, U: c.WeakTypeTag](
    c: blackbox.Context,
  )(path: c.Expr[T => U]): c.Tree = {
    import c.universe._
    val configureOpTree = q"_root_.difflicious.ConfigureOp.ignore"
    toConfigureRawCall(c)(path, configureOpTree)
  }

  @nowarn("msg=.*never used.*")
  def toConfigureRawCall[T: c.WeakTypeTag, U: c.WeakTypeTag](
    c: blackbox.Context,
  )(path: c.Expr[T => U], op: c.Tree): c.Tree = {
    import c.universe._

    // When deriving, Magnolia derives for all subtypes in the hierarchy
    // (including subclasses of sub-sealed traits) therefore when checking
    // We need to resolve all subclasses in the hierarchy
    @tailrec
    def resolveAllSubtypesInHierarchy(toCheck: Vector[Symbol], accum: Vector[Symbol]): Vector[Symbol] = {
      if (toCheck.isEmpty) accum
      else {
        val newLeafSubTypes = mutable.ArrayBuffer.from(toCheck)
        val nextToCheck = mutable.ArrayBuffer.empty[Symbol]
        toCheck.foreach { s =>
          val subTypes = s.asClass.knownDirectSubclasses
          if (subTypes.isEmpty) {
            newLeafSubTypes += s
          } else {
            nextToCheck ++= subTypes
          }
        }

        resolveAllSubtypesInHierarchy(nextToCheck.toVector, accum ++ newLeafSubTypes)
      }
    }

    @tailrec
    def collectPathElements(tree: c.Tree, acc: List[String]): List[String] = {
      tree match {
        case q"$parent.$child" => {
          collectPathElements(parent, child.decodedName.toString :: acc)
        }
        case _: Ident => acc
        case q"$func[..$tArgs]($t)($ev)" => {
          collectPathElements(t, acc)
        }
        case q"$func[$superType]($rest).subType[$subType]" => {
          val superTypeSym = superType.symbol.asClass
          val subTypeSym = subType.symbol.asClass

          val allKnownSubTypesInHierarchy =
            resolveAllSubtypesInHierarchy(toCheck = superTypeSym.knownDirectSubclasses.toVector, accum = Vector.empty)
          if (allKnownSubTypesInHierarchy.contains(subTypeSym)) {
            collectPathElements(rest, subTypeSym.name.decodedName.toString :: acc)
          } else {
            c.abort(
              c.enclosingPosition,
              s"""Specified subtype is not a known direct subtype of $superTypeSym.
                 |The supertype needs to be sealed, and you might need to ensure that both the supertype 
                 |and subtype gets compiled before this invocation.
                 |See also: <https://issues.scala-lang.org/browse/SI-7046>.""".stripMargin,
            )
          }
        }
        case _ =>
          c.abort(c.enclosingPosition, s"$requiredShapeMsg, got: ${tree}")
      }
    }

    path.tree match {
      case q"($_) => $pathBody" => {
        val pathStr = collectPathElements(pathBody, List.empty)
        q"""${c.prefix.tree}.configureRaw(
            _root_.difflicious.ConfigurePath.fromPath($pathStr),
            $op
          ) match {
            case Right(newDiffer) => newDiffer
            case Left(e) => throw e
          }
         """
      }
      case _ => c.abort(c.enclosingPosition, s"$requiredShapeMsg, got: ${path.tree}")
    }

  }

}

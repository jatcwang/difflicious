package difflicious.internal

import difflicious.Differ
import difflicious.utils.Pairable

import scala.collection.mutable
import scala.annotation.{tailrec, nowarn}
import scala.reflect.macros.blackbox

trait ConfigureImpl[T] { this: Differ[T] =>
  def configureIgnore[U](path: T => U): Differ[T] = macro ConfigureMacro.configureIgnore_impl[T, U]

  def configurePairBy[F[_]: Pairable, A, B](path: T => F[A])(pairBy: A => B): Differ[T] =
    macro ConfigureMacro.configurePairBy_impl[F, T, A, B]
}

// Implementation inspired by quicklen's path macro.
// See https://github.com/softwaremill/quicklens/blob/c2fd335b80f3d4d55a76d146d8308d95575dd749/quicklens/src/main/scala-2/com/softwaremill/quicklens/QuicklensMacros.scala
object ConfigureMacro {

  val requiredShapeMsg = "Configure path must have shape like: _.field1.each.field2.subType[ASubClass]"

  def configurePairBy_impl[F[_], T, A, B](
    c: blackbox.Context,
  )(path: c.Expr[T => F[A]])(pairBy: c.Expr[A => B])(ev: c.Expr[Pairable[F]]): c.Tree = {
    import c.universe._

    val _ = ev // unused

    val opTree = q"_root_.difflicious.ConfigureOp.PairBy.func(${pairBy})"
    toConfigureRawCall(c)(path, opTree)
  }

  @nowarn("msg=.*never used.*")
  def toConfigureRawCall[T: c.WeakTypeTag, U: c.WeakTypeTag](
    c: blackbox.Context,
  )(path: c.Expr[T => U], op: c.Tree): c.Tree = {
    import c.universe._

    // When deriving, Magnolia derives for all subtypes in the hierarchy (including nested) therefore when checking
    // We need to do this too
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
            // FIXME: check err msg
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

    // FIXME:
//    println(path.tree)
//    println(showRaw(path.tree))
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

  // FIXME: allow focusing on 'subClass'
  // FIXME: calling a method seems to fail
  // FIXME: special char in field names
  def configureIgnore_impl[T: c.WeakTypeTag, U: c.WeakTypeTag](
    c: blackbox.Context,
  )(path: c.Expr[T => U]): c.Tree = {
    import c.universe._
    val configureOpTree = q"_root_.difflicious.ConfigureOp.ignore"
    toConfigureRawCall(c)(path, configureOpTree)
  }

}

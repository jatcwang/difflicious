package difflicious.internal

import difflicious.Differ

import scala.collection.mutable
import scala.annotation.{nowarn, tailrec}
import scala.reflect.macros.blackbox

trait ConfigureMethods[T] { this: Differ[T] =>

  def ignoreAt[U](path: T => U): Differ[T] = macro ConfigureMacro.ignoreAt_impl[T, U]

  // format: off
  def configure[U](path: T => U)(configFunc: Differ[U] => Differ[U]): Differ[T] =
    macro ConfigureMacro.configure_impl[T, U]
  // format: on

  def replace[U](path: T => U)(newDiffer: Differ[U]): Differ[T] = macro ConfigureMacro.replace_impl[T, U]
}

// Implementation inspired by quicklen's path macro.
// See https://github.com/softwaremill/quicklens/blob/c2fd335b80f3d4d55a76d146d8308d95575dd749/quicklens/src/main/scala-2/com/softwaremill/quicklens/QuicklensMacros.scala
object ConfigureMacro {

  val requiredShapeMsg = "Configure path must have shape like: _.field1.each.field2.subType[ASubClass]"

  def configure_impl[T, U](c: blackbox.Context)(
    path: c.Expr[T => U],
  )(
    configFunc: c.Expr[Differ[U] => Differ[U]],
  ): c.Tree = {
    import c.universe.*
    val opTree = q"_root_.difflicious.ConfigureOp.TransformDiffer($configFunc)"
    toConfigureRawCall(c)(path, opTree)
  }

  def replace_impl[T, U](c: blackbox.Context)(
    path: c.Expr[T => U],
  )(
    newDiffer: c.Expr[Differ[U]],
  )(implicit uTypeTag: c.WeakTypeTag[U]): c.Tree = {
    import c.universe.*
    val opTree =
      q"_root_.difflicious.ConfigureOp.TransformDiffer[${uTypeTag.tpe}](unused => { val _ = unused; $newDiffer })"
    toConfigureRawCall(c)(path, opTree)
  }

  def ignoreAt_impl[T: c.WeakTypeTag, U: c.WeakTypeTag](
    c: blackbox.Context,
  )(path: c.Expr[T => U]): c.Tree = {
    import c.universe.*
    val configureOpTree = q"_root_.difflicious.ConfigureOp.ignore"
    toConfigureRawCall(c)(path, configureOpTree)
  }

  @nowarn("msg=.*never used.*")
  def toConfigureRawCall[T: c.WeakTypeTag, U: c.WeakTypeTag](
    c: blackbox.Context,
  )(path: c.Expr[T => U], op: c.Tree): c.Tree = {
    import c.universe.*

    // Derivation includes all leaf subtypes in the hierarchy, including
    // subclasses of sub-sealed traits, so path checking resolves them too.
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
    def collectPathElements(tree: c.Tree, acc: List[c.Tree]): List[c.Tree] = {
      def isImplicitConversion(func: c.Tree): Boolean =
        func.symbol != NoSymbol && func.symbol.isImplicit

      tree match {
        case q"$parent.$child" => {
          collectPathElements(parent, q"${child.decodedName.toString}" :: acc)
        }
        case _: Ident => acc
        case q"$func[..$tArgs]($t)($ev)" => {
          collectPathElements(t, acc)
        }
        case q"$func[$superType]($rest).subType[$subType]($relationship)" => {
          collectPathElements(rest, q"$relationship.path" :: acc)
        }
        case q"$func[$superType]($rest).subType[$subType]" => {
          val superTypeTpe = rest.tpe.dealias
          val subTypeTpe = subType.tpe.dealias

          if (subTypeTpe <:< superTypeTpe) {
            val superTypeSym = superTypeTpe.typeSymbol.asClass
            val subTypeSym = subTypeTpe.typeSymbol.asClass
            val allKnownSubTypesInHierarchy =
              resolveAllSubtypesInHierarchy(toCheck = superTypeSym.knownDirectSubclasses.toVector, accum = Vector.empty)
            if (allKnownSubTypesInHierarchy.contains(subTypeSym)) {
              collectPathElements(rest, q"${subTypeSym.name.decodedName.toString}" :: acc)
            } else {
              c.abort(
                c.enclosingPosition,
                s"""Specified subtype is not a known direct subtype of $superTypeSym.
                   |The supertype needs to be sealed, and you might need to ensure that both the supertype
                   |and subtype gets compiled before this invocation.
                   |See also: <https://issues.scala-lang.org/browse/SI-7046>.""".stripMargin,
              )
            }
          } else {
            val relationshipType = appliedType(
              c.mirror.staticClass("difflicious.DifferSubTypeRelationship").toTypeConstructor,
              superTypeTpe :: subTypeTpe :: Nil,
            )
            val relationship = c.inferImplicitValue(relationshipType, silent = true)
            if (relationship != EmptyTree) {
              collectPathElements(rest, q"$relationship.path" :: acc)
            } else {
              c.abort(c.enclosingPosition, s"$requiredShapeMsg, got: ${tree}")
            }
          }
        }
        case q"$func($t)" if isImplicitConversion(func) => {
          collectPathElements(t, acc)
        }
        case _ =>
          c.abort(c.enclosingPosition, s"$requiredShapeMsg, got: ${tree}")
      }
    }

    path.tree match {
      case q"($_) => $pathBody" => {
        val pathElements = collectPathElements(pathBody, List.empty)
        q"""${c.prefix.tree}.configureRaw(
            _root_.difflicious.ConfigurePath.fromPath(_root_.scala.List(..$pathElements)),
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

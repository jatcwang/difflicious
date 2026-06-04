package difflicious

import difflicious.differ.{OneOfDiffer, ValueDiffer}
import difflicious.utils.{MapLike, SeqLike, SetLike, TypeName}
import hearth.fp.effect.*
import hearth.fp.instances.*
import hearth.fp.syntax.*

import scala.collection.immutable.ListMap
import scala.util.control.NoStackTrace

private[difflicious] trait DifferDerivationMacros { this: hearth.MacroCommons =>

  private lazy val OptionCtor = Type.Ctor1.of[Option]
  private lazy val EitherCtor = Type.Ctor2.of[Either]

  private val derivedDiffersCache: MLocal[ValDefsCache] = ValDefsCache.mlocal

  def deriveDiffer[A: Type](deriveIfMissing: Boolean): Expr[Differ[A]] = {
    Log
      .namedScope("Derivation entrypoint") {
        deriveDifferMIO[A](deriveIfMissing)
      }
      .runToExprOrFail(
        s"difflicious.Differ[${Type[A].plainPrint}]",
        infoRendering = if (shouldWeLogDerivation) RenderFrom(Log.Level.Info) else DontRender,
      ) { (_, errors) =>
        renderDerivationErrors[A](errors.toVector, deriveIfMissing)
      }
  }

  def deriveAutoDiffer[A: Type](
    reportError: String => Unit,
    fallback: => Expr[Differ[A]],
  ): Expr[Differ[A]] =
    Log
      .namedScope("Derivation entrypoint") {
        deriveDifferMIO[A](deriveIfMissing = true)
      }
      .attempt
      .map {
        case Right(differ) => differ
        case Left(errors) =>
          reportError(renderDerivationErrors[A](errors.toVector, deriveIfMissing = true))
          fallback
      }
      .runToExprOrFail(
        s"difflicious.Differ[${Type[A].plainPrint}]",
        infoRendering = if (shouldWeLogDerivation) RenderFrom(Log.Level.Info) else DontRender,
      ) { (_, errors) =>
        renderDerivationErrors[A](errors.toVector, deriveIfMissing = true)
      }

  private def deriveDifferMIO[A: Type](deriveIfMissing: Boolean): MIO[Expr[Differ[A]]] = {
    cachedDerive[A](deriveIfMissing)
      .flatMap { differ =>
        derivedDiffersCache.get.map { cache =>
          cache.toValDefs.use { _ =>
            differ
          }
        }
      }
  }

  private def derive[A: Type](deriveIfMissing: Boolean): MIO[Expr[Differ[A]]] =
    CaseClass
      .parse[A]
      .toOption
      .map(deriveCaseClass[A](_, deriveIfMissing))
      .orElse(Enum.parse[A].toOption.map(deriveEnum[A](_, deriveIfMissing)))
      .orElse(SingletonValue.parse[A].toOption.map(_ => deriveSingleton[A]))
      .getOrElse {
        failDerivation(
          "expected a case class, sealed hierarchy, enum, or singleton",
        )
      }

  private def deriveCaseClass[A: Type](
    caseClass: CaseClass[A],
    deriveIfMissing: Boolean,
  ): MIO[Expr[Differ[A]]] = {
    val constructorFields = caseClass.caseFields.filter { field =>
      import field.Underlying as Field

      field.value.asInstanceOf[Method[A, Field]].isConstructorArgument
    }

    val fields = constructorFields.zipWithIndex.toList.parTraverse { case (field, index) =>
      import field.Underlying as Field

      val fieldName = decodedName(field.value.name)
      val getter = productGetterFor[A](index)
      summonFieldDiffer[A, Field](deriveIfMissing).map { differ =>
        Expr.quote {
          Tuple2(
            Expr.splice(Expr(fieldName)),
            Tuple2(
              Expr.splice(getter),
              Expr.splice(differ).asInstanceOf[difflicious.Differ[Any]],
            ),
          )
        }
      }
    }

    fields.map { fields =>
      Expr.quote {
        new difflicious.differ.RecordDiffer[A](
          fieldDiffers = Expr.splice(listMapExpr[A](fields)),
          isIgnored = false,
          typeName = Expr.splice(typeNameExpr[A]),
        )
      }
    }
  }

  private def deriveEnum[A: Type](
    enumm: Enum[A],
    deriveIfMissing: Boolean,
  ): MIO[Expr[Differ[A]]] = {
    val children =
      enumm.exhaustiveChildren.map(_.toList).getOrElse(enumm.directChildren.toList).map(_._2)

    if (children.isEmpty) {
      failDerivation(
        "sealed hierarchy has no known children",
      )
    } else {
      children.parTraverse(deriveSubtypeCase[A](_, deriveIfMissing)).map { cases =>
        Expr.quote {
          new difflicious.differ.OneOfDiffer[A](
            cases = Expr.splice(vectorExpr[A](cases)),
            isIgnored = false,
            differTypeName = "OneOfDiffer",
          )
        }
      }
    }
  }

  private def deriveSingleton[A: Type]: MIO[Expr[Differ[A]]] =
    MIO.pure {
      Expr.quote {
        new difflicious.differ.RecordDiffer[A](
          fieldDiffers = scala.collection.immutable.ListMap.empty[String, (A => Any, difflicious.Differ[Any])],
          isIgnored = false,
          typeName = Expr.splice(typeNameExpr[A]),
        )
      }
    }

  private def deriveSubtypeCase[A: Type](
    subtype: ??<:[A],
    deriveIfMissing: Boolean,
  ): MIO[Expr[OneOfDiffer.Case[A, Any]]] = {
    import subtype.Underlying as B

    summonOrDerive[B](deriveIfMissing).attempt.flatMap {
      case Right(differ) =>
        val typeName = typeNameExpr[B]

        MIO.pure {
          Expr.singletonOf[B] match {
            case Some(singleton) =>
              singletonSubtypeCase[A, B](typeName, differ, singleton)
            case None =>
              subtypeCase[A, B](typeName, differ)
          }
        }
      case Left(errors) =>
        failDerivationError(
          DifferDerivationError.FailedDiffer(
            differType = typeNameForError[B],
            errors = derivationErrors(errors.toVector),
          ),
        )
    }
  }

  protected def subtypeCase[A: Type, B: Type](
    typeName: Expr[TypeName.SomeTypeName],
    differ: Expr[Differ[B]],
  ): Expr[OneOfDiffer.Case[A, Any]]

  protected def singletonSubtypeCase[A: Type, B: Type](
    typeName: Expr[TypeName.SomeTypeName],
    differ: Expr[Differ[B]],
    singleton: Expr[B],
  ): Expr[OneOfDiffer.Case[A, Any]]

  protected final class AppliedType1(
    val typeConstructor: UntypedType,
    val itemType: ??,
  )

  protected final class AppliedType2(
    val typeConstructor: UntypedType,
    val keyType: ??,
    val valueType: ??,
  )

  protected def appliedType1[A: Type]: Option[AppliedType1]

  protected def appliedType2[A: Type]: Option[AppliedType2]

  protected def appliedType(typeConstructor: UntypedType, typeArguments: UntypedType*): UntypedType

  private def summonImplicitAs[A](tpe: UntypedType): Option[Expr[A]] = {
    implicit val typed: Type[A] = UntypedType.toTyped[A](tpe)
    Expr.summonImplicit[A].toOption
  }

  private def constructDiffer[A: Type](
    differType: UntypedType,
    arguments: UntypedArguments,
  ): Expr[Differ[A]] = {
    implicit val differAType: Type[Differ[A]] = differTypeInstance[A]
    differType.primaryConstructor
      .getOrElse {
        throw new IllegalStateException(s"Unable to find primary constructor for ${differType.plainPrint}")
      }
      .unsafeApplyNoInstance(differType)(arguments)
      .asTyped[Differ[A]]
  }

  protected final def summonContainerDiffer[A: Type](
    deriveIfMissing: Boolean,
  ): MIO[Option[Expr[Differ[A]]]] =
    summonOptionDiffer[A](deriveIfMissing).flatMap {
      case some @ Some(_) => MIO.pure(some)
      case None =>
        summonEitherDiffer[A](deriveIfMissing).flatMap {
          case some @ Some(_) => MIO.pure(some)
          case None =>
            summonMapDiffer[A](deriveIfMissing).flatMap {
              case some @ Some(_) => MIO.pure(some)
              case None => summonSeqOrSetDiffer[A](deriveIfMissing)
            }
        }
    }

  private def summonOptionDiffer[A: Type](
    deriveIfMissing: Boolean,
  ): MIO[Option[Expr[Differ[A]]]] =
    Type[A] match {
      case OptionCtor(itemType) =>
        import itemType.Underlying as Item

        summonNestedDiffer[Item](deriveIfMissing).map {
          _.map { itemDiffer =>
            Expr
              .quote {
                implicit val valueDiffer: difflicious.Differ[Item] = Expr.splice(itemDiffer)

                difflicious.Differ.optionDiffer[Item]
              }
              .asInstanceOf[Expr[Differ[A]]]
          }
        }
      case _ =>
        MIO.pure(None)
    }

  private def summonEitherDiffer[A: Type](
    deriveIfMissing: Boolean,
  ): MIO[Option[Expr[Differ[A]]]] =
    Type[A] match {
      case EitherCtor(leftType, rightType) =>
        import leftType.Underlying as L
        import rightType.Underlying as R

        summonNestedDiffer[L](deriveIfMissing).flatMap {
          case Some(leftDiffer) =>
            summonNestedDiffer[R](deriveIfMissing).map {
              _.map { rightDiffer =>
                Expr
                  .quote {
                    implicit val leftValueDiffer: difflicious.Differ[L] = Expr.splice(leftDiffer)
                    implicit val rightValueDiffer: difflicious.Differ[R] = Expr.splice(rightDiffer)

                    difflicious.Differ.eitherDiffer[L, R]
                  }
                  .asInstanceOf[Expr[Differ[A]]]
              }
            }
          case None =>
            MIO.pure(None)
        }
      case _ =>
        MIO.pure(None)
    }

  private def summonMapDiffer[A: Type](
    deriveIfMissing: Boolean,
  ): MIO[Option[Expr[Differ[A]]]] =
    appliedType2[A] match {
      case Some(applied) =>
        val typeConstructor = applied.typeConstructor
        val keyType = applied.keyType
        val valueType = applied.valueType
        import keyType.Underlying as Key
        import valueType.Underlying as Value

        val keyDiffer =
          summonImplicitAs[ValueDiffer[Key]](appliedType(Type.of[ValueDiffer[Any]].asUntyped, keyType.asUntyped))
        val typeName =
          summonImplicitAs[TypeName[Map[Key, Value]]](appliedType(Type.of[TypeName[Any]].asUntyped, Type[A].asUntyped))
        val asMap = summonImplicitAs[MapLike[Map]](appliedType(Type.of[MapLike[Map]].asUntyped, typeConstructor))

        (keyDiffer, typeName, asMap) match {
          case (Some(keyDiffer), Some(typeName), Some(asMap)) =>
            summonNestedDiffer[Value](deriveIfMissing).map {
              _.map { valueDiffer =>
                constructDiffer[A](
                  appliedType(
                    Type.of[difflicious.differ.MapDiffer[Map, Any, Any]].asUntyped,
                    typeConstructor,
                    keyType.asUntyped,
                    valueType.asUntyped,
                  ),
                  Map(
                    "isIgnored" -> Expr(false).asUntyped,
                    "keyDiffer" -> keyDiffer.asUntyped,
                    "valueDiffer" -> valueDiffer.asUntyped,
                    "typeName" -> typeName.asUntyped,
                    "asMap" -> asMap.asUntyped,
                  ),
                )
              }
            }
          case _ =>
            MIO.pure(None)
        }
      case None =>
        MIO.pure(None)
    }

  private def summonSeqOrSetDiffer[A: Type](
    deriveIfMissing: Boolean,
  ): MIO[Option[Expr[Differ[A]]]] =
    appliedType1[A] match {
      case Some(applied) =>
        val typeConstructor = applied.typeConstructor
        val itemType = applied.itemType
        summonSeqDiffer[A](typeConstructor, itemType, deriveIfMissing).flatMap {
          case some @ Some(_) => MIO.pure(some)
          case None => summonSetDiffer[A](typeConstructor, itemType, deriveIfMissing)
        }
      case None =>
        MIO.pure(None)
    }

  private def summonSeqDiffer[A: Type](
    typeConstructor: UntypedType,
    itemType: ??,
    deriveIfMissing: Boolean,
  ): MIO[Option[Expr[Differ[A]]]] = {
    import itemType.Underlying as Item

    val typeName =
      summonImplicitAs[TypeName[List[Item]]](appliedType(Type.of[TypeName[Any]].asUntyped, Type[A].asUntyped))
    val asSeq = summonImplicitAs[SeqLike[List]](appliedType(Type.of[SeqLike[List]].asUntyped, typeConstructor))

    (typeName, asSeq) match {
      case (Some(typeName), Some(asSeq)) =>
        summonNestedDiffer[Item](deriveIfMissing).map {
          _.map { itemDiffer =>
            constructDiffer[A](
              appliedType(
                Type.of[difflicious.differ.SeqDiffer[List, Any]].asUntyped,
                typeConstructor,
                itemType.asUntyped,
              ),
              Map(
                "isIgnored" -> Expr(false).asUntyped,
                "pairBy" -> Expr.quote { difflicious.ConfigureOp.PairBy.Index }.asUntyped,
                "itemDiffer" -> itemDiffer.asUntyped,
                "typeName" -> typeName.asUntyped,
                "asSeq" -> asSeq.asUntyped,
              ),
            )
          }
        }
      case _ =>
        MIO.pure(None)
    }
  }

  private def summonSetDiffer[A: Type](
    typeConstructor: UntypedType,
    itemType: ??,
    deriveIfMissing: Boolean,
  ): MIO[Option[Expr[Differ[A]]]] = {
    import itemType.Underlying as Item

    val typeName =
      summonImplicitAs[TypeName[Set[Item]]](appliedType(Type.of[TypeName[Any]].asUntyped, Type[A].asUntyped))
    val asSet = summonImplicitAs[SetLike[Set]](appliedType(Type.of[SetLike[Set]].asUntyped, typeConstructor))

    (typeName, asSet) match {
      case (Some(typeName), Some(asSet)) =>
        summonNestedDiffer[Item](deriveIfMissing).map {
          _.map { itemDiffer =>
            constructDiffer[A](
              appliedType(
                Type.of[difflicious.differ.SetDiffer[Set, Any]].asUntyped,
                typeConstructor,
                itemType.asUntyped,
              ),
              Map(
                "isIgnored" -> Expr(false).asUntyped,
                "itemDiffer" -> itemDiffer.asUntyped,
                "matchFunc" -> Expr.quote { (value: Item) =>
                  value: Any
                }.asUntyped,
                "typeName" -> typeName.asUntyped,
                "asSet" -> asSet.asUntyped,
              ),
            )
          }
        }
      case _ =>
        MIO.pure(None)
    }
  }

  private def summonOrDerive[A: Type](deriveIfMissing: Boolean): MIO[Expr[Differ[A]]] = {
    implicit val differAType: Type[Differ[A]] = differTypeInstance[A]
    Expr.summonImplicit[Differ[A]].toOption match {
      case Some(differ) => Log.info(s"Found Differ[${typeNameForError[A]}] in implicit scope") >> MIO.pure(differ)
      case None => cachedDerive[A](deriveIfMissing)
    }
  }

  private def summonFieldDiffer[A: Type, Field: Type](
    deriveIfMissing: Boolean,
  ): MIO[Expr[Differ[Field]]] = {
    summonNestedDiffer[Field](deriveIfMissing).attempt.flatMap {
      case Right(Some(differ)) => MIO.pure(differ)
      case Right(None) =>
        failDerivationError(
          DifferDerivationError.MissingDiffer(
            differType = typeNameForError[Field],
            deriveIfMissing = deriveIfMissing,
          ),
        )
      case Left(errors) =>
        failDerivationError(
          DifferDerivationError.FailedDiffer(
            differType = typeNameForError[Field],
            errors = derivationErrors(errors.toVector),
          ),
        )
    }
  }

  private def derivationErrors(errors: Vector[Throwable]): Vector[DifferDerivationError] =
    deduplicateDerivationErrors {
      errors.map {
        case error: DifferDerivationError => normalizeDerivationError(error)
        case other => DifferDerivationError.Other(Option(other.getMessage).getOrElse(other.toString))
      }
    }

  private def normalizeDerivationError(error: DifferDerivationError): DifferDerivationError =
    error match {
      case DifferDerivationError.FailedDiffer(differType, errors) =>
        DifferDerivationError.FailedDiffer(
          differType,
          deduplicateDerivationErrors(errors.map(normalizeDerivationError)),
        )
      case other => other
    }

  private def deduplicateDerivationErrors(
    errors: Vector[DifferDerivationError],
  ): Vector[DifferDerivationError] = {
    val initial = (Vector.empty[DifferDerivationError], Set.empty[String])

    errors
      .foldLeft(initial) { case ((deduplicated, seen), error) =>
        val key = derivationErrorKey(error)

        if (seen(key)) {
          (deduplicated, seen)
        } else {
          (deduplicated :+ error, seen + key)
        }
      }
      ._1
  }

  private def derivationErrorKey(error: DifferDerivationError): String =
    error match {
      case DifferDerivationError.Other(message) => s"other:$message"
      case DifferDerivationError.MissingDiffer(differType, _) => s"differ:$differType"
      case DifferDerivationError.FailedDiffer(differType, _) => s"differ:$differType"
    }

  protected final def summonNestedDiffer[Field: Type](
    deriveIfMissing: Boolean,
  ): MIO[Option[Expr[Differ[Field]]]] = {
    implicit val differFieldType: Type[Differ[Field]] = differTypeInstance[Field]
    Expr.summonImplicit[Differ[Field]].toOption match {
      case Some(differ) =>
        Log.info(s"Found Differ[${typeNameForError[Field]}] in implicit scope") >>
          MIO.pure(Some(differ))
      case None =>
        cachedDifferRef[Field].flatMap {
          case Some(differ) => MIO.pure(Some(differ))
          case None =>
            summonContainerDiffer[Field](deriveIfMissing).flatMap {
              case Some(differ) => MIO.pure(Some(differ))
              case None if deriveIfMissing && canDerive[Field] =>
                cachedDerive[Field](deriveIfMissing).map(Some(_))
              case None => MIO.pure(None)
            }
        }
    }
  }

  protected final def cachedDifferRef[A: Type]: MIO[Option[Expr[Differ[A]]]] = {
    implicit val differAType: Type[Differ[A]] = differTypeInstance[A]
    derivedDiffersCache.get0Ary[Differ[A]](derivationCacheKey[A])
  }

  protected final def isDerivable[A: Type]: Boolean =
    canDerive[A]

  private def cachedDerive[A: Type](deriveIfMissing: Boolean): MIO[Expr[Differ[A]]] = {
    implicit val differAType: Type[Differ[A]] = differTypeInstance[A]

    val cacheKey = derivationCacheKey[A]

    derivedDiffersCache.get0Ary[Differ[A]](cacheKey).flatMap {
      case Some(differ) =>
        Log.info(s"Found in this derivation's cache: Differ[${typeNameForError[A]}]") >>
          MIO.pure(differ)
      case None =>
        Log.namedScope(s"Deriving Differ[${typeNameForError[A]}]") {
          val builder = ValDefBuilder.ofLazy[Differ[A]]("derivedDiffer")

          for {
            _ <- derivedDiffersCache.forwardDeclare(cacheKey, builder)
            differ <- derivedDiffersCache.get0Ary[Differ[A]](cacheKey).flatMap {
              case Some(differ) => MIO.pure(differ)
              case None =>
                failDerivation(
                  "internal error, cache entry was not forward declared",
                )
            }
            body <- derive[A](deriveIfMissing)
            _ <- derivedDiffersCache.buildCachedWith(cacheKey, builder) { _ =>
              Expr.quote {
                new difflicious.differ.LazyDiffer[A](Expr.splice(body))
              }
            }
          } yield differ
        }
    }
  }

  private def canDerive[A: Type]: Boolean =
    CaseClass.parse[A].toOption.isDefined ||
      Enum.parse[A].toOption.isDefined ||
      SingletonValue.parse[A].toOption.isDefined

  private def failDerivation[A](message: String): MIO[A] =
    failDerivationError(DifferDerivationError.Other(message))

  private def failDerivationError[A](error: DifferDerivationError): MIO[A] =
    MIO.fail(error)

  private def renderDerivationErrors[A: Type](
    errors: Vector[Throwable],
    deriveIfMissing: Boolean,
  ): String = {
    val targetType = typeNameForError[A]
    val structuredErrors = derivationErrors(errors)
    val renderedErrors = renderDerivationErrorLines(structuredErrors, indent = "  ")
    val renderedSummary = renderDerivationSummary(structuredErrors, deriveIfMissing)

    val renderedTree =
      s"""Failed to derive Differ[$targetType]
         |
         |$targetType
         |${renderedErrors.mkString("\n")}""".stripMargin

    if (renderedSummary.isEmpty) renderedTree
    else s"$renderedTree\n\n${renderedSummary.mkString("\n")}"
  }

  private def renderDerivationSummary(
    errors: Vector[DifferDerivationError],
    deriveIfMissing: Boolean,
  ): Vector[String] = {
    val leafFailures =
      if (deriveIfMissing) leafFailureDifferTypes(errors).distinct
      else Vector.empty

    if (leafFailures.isEmpty) Vector.empty
    else {
      Vector(
        s"Summary: Derivation failed because we couldn't derive ${leafFailures.map(differType => s"Differ[$differType]").mkString(", ")}",
      )
    }
  }

  private def leafFailureDifferTypes(errors: Vector[DifferDerivationError]): Vector[String] =
    errors.flatMap(leafFailureDifferTypes)

  private def leafFailureDifferTypes(error: DifferDerivationError): Vector[String] =
    error match {
      case DifferDerivationError.MissingDiffer(differType, _) => Vector(differType)
      case DifferDerivationError.FailedDiffer(differType, errors) =>
        val childFailures = leafFailureDifferTypes(errors)

        if (childFailures.isEmpty) Vector(differType)
        else childFailures
      case DifferDerivationError.Other(_) => Vector.empty
    }

  private def renderDerivationErrorLines(
    errors: Vector[DifferDerivationError],
    indent: String,
  ): Vector[String] =
    errors.flatMap(renderDerivationErrorLines(_, indent))

  private def renderDerivationErrorLines(
    error: DifferDerivationError,
    indent: String,
  ): Vector[String] =
    error match {
      case DifferDerivationError.Other(message) =>
        message.linesIterator.map(line => s"$indent$line").toVector
      case DifferDerivationError.MissingDiffer(differType, deriveIfMissing) =>
        Vector(
          s"${indent}Differ[$differType] ${DifferDerivationError.missingDifferMessage(deriveIfMissing)}",
        )
      case DifferDerivationError.FailedDiffer(differType, errors) =>
        s"${indent}Differ[$differType] cannot be derived because..." +:
          renderDerivationErrorLines(errors, s"$indent  ")
    }

  private def productGetterFor[A: Type](index: Int): Expr[A => Any] =
    Expr.quote { (value: A) =>
      value.asInstanceOf[scala.Product].productElement(Expr.splice(Expr(index)))
    }

  private def listMapExpr[A: Type](
    fields: List[Expr[(String, (A => Any, Differ[Any]))]],
  ): Expr[ListMap[String, (A => Any, Differ[Any])]] =
    fields.foldLeft(Expr.quote(ListMap.empty[String, (A => Any, difflicious.Differ[Any])])) { (acc, field) =>
      Expr.quote {
        Expr.splice(acc) + Expr.splice(field)
      }
    }

  private def vectorExpr[A: Type](
    cases: List[Expr[OneOfDiffer.Case[A, Any]]],
  ): Expr[Vector[OneOfDiffer.Case[A, Any]]] = {
    implicit val caseAType: Type[OneOfDiffer.Case[A, Any]] = oneOfCaseTypeInstance[A]

    Expr.quote {
      Vector(Expr.splice(VarArgs(cases*))*)
    }
  }

  private def typeNameExpr[A: Type]: Expr[TypeName.SomeTypeName] =
    Expr.quote {
      difflicious.utils.TypeName[Any](
        long = Expr.splice(Expr(decodedName(Type[A].fqcn))),
        short = Expr.splice(Expr(decodedName(Type[A].shortName))),
        typeArguments = Nil,
      )
    }

  private def decodedName(name: String): String =
    scala.reflect.NameTransformer.decode(name)

  private val QualifiedName =
    """(?:[A-Za-z_$][\w$]*\.)+([A-Za-z_$][\w$]*)""".r

  private def typeNameForError[A: Type]: String =
    conciseTypeName(Type[A].plainPrint)

  private def conciseTypeName(typeName: String): String =
    QualifiedName.replaceAllIn(decodedName(typeName), _.group(1))

  private def differTypeInstance[A: Type]: Type[Differ[A]] =
    Type.of[Differ[A]]

  private def oneOfCaseTypeInstance[A: Type]: Type[OneOfDiffer.Case[A, Any]] =
    Type.of[OneOfDiffer.Case[A, Any]]

  private def derivationCacheKey[A: Type]: String =
    s"${Type[A].plainPrint}"

  private def shouldWeLogDerivation: Boolean = {
    implicit val logDerivationType: Type[debug.LogDerivation] = logDerivationTypeInstance

    Expr.summonImplicit[debug.LogDerivation].isDefined
  }

  private def logDerivationTypeInstance: Type[debug.LogDerivation] =
    Type.of[debug.LogDerivation]
}

private[difflicious] sealed abstract class DifferDerivationError(message: String)
    extends RuntimeException(message)
    with NoStackTrace

private[difflicious] object DifferDerivationError {
  final case class Other(message: String) extends DifferDerivationError(message)

  def missingDifferMessage(deriveIfMissing: Boolean): String =
    if (deriveIfMissing) "cannot be found or derived"
    else "cannot be found"

  final case class MissingDiffer(differType: String, deriveIfMissing: Boolean)
      extends DifferDerivationError(s"Differ[$differType] ${missingDifferMessage(deriveIfMissing)}")

  final case class FailedDiffer(
    differType: String,
    errors: Vector[DifferDerivationError],
  ) extends DifferDerivationError(s"Differ[$differType] cannot be derived because...")
}

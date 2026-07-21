package difflicious

import difflicious.differ.{LazyDiffer, OneOfDiffer, ProductDiffer, ValueDiffer}
import difflicious.utils.{MapLike, SeqLike, SetLike, TypeName}
import hearth.fp.effect.*
import hearth.fp.instances.*
import hearth.fp.syntax.*

import scala.annotation.nowarn
import scala.util.control.NoStackTrace

private[difflicious] trait DifferDerivationMacrosCommon { this: hearth.MacroCommons =>
  private val logDerivationTypeInstance: Type[debug.LogDerivation] =
    Type.of[debug.LogDerivation]

  protected def mkCollectionHelper1[A: Type]: Option[CollectionHelper1[A]]

  protected def mkCollectionHelper2[A: Type]: Option[CollectionHelper2[A]]

  private val derivedDiffersCache: MLocal[ValDefsCache] = ValDefsCache.mlocal

  private val failedDerivedDiffersCache: MLocal[Map[String, DifferDerivationError]] =
    MLocal.unsafeSharedParallel(Map.empty)

  private lazy val ignoredDifferImplicits: Seq[UntypedMethod] =
    Type
      .of[generic.AutoDerivation]
      .asUntyped
      .methods
      .filter(_.name == "autoDerivedDiffer")

  private def derivationCacheKey[A: Type]: String =
    s"${Type[A].plainPrint}"

  private lazy val differCtor: Type.Ctor1[Differ] = Type.Ctor1.of[Differ]

  private lazy val valueDifferCtor: Type.Ctor1[ValueDiffer] = Type.Ctor1.of[ValueDiffer]

  private lazy val optionCtor: Type.Ctor1[Option] = Type.Ctor1.of[Option]

  private lazy val eitherCtor: Type.Ctor2[Either] = Type.Ctor2.of[Either]

  private def differType[A: Type]: Type[Differ[A]] =
    differCtor.apply[A]

  private def fieldDifferEntryType: Type[(String, Differ[Any])] =
    Type.of[(String, Differ[Any])]

  private def oneOfCaseAnyType[A: Type]: Type[OneOfDiffer.Case[A, Any]] =
    Type.of[OneOfDiffer.Case[A, Any]]

  private def shouldWeLogDerivation: Boolean = {
    implicit val logDerivationType: Type[debug.LogDerivation] = logDerivationTypeInstance

    Expr.summonImplicit[debug.LogDerivation].isDefined
  }

  /** Entry point for explicit derivation.
    *
    * Handles both `derived` and `derivedDeep`, with `isCascadeDerive` deciding whether missing field differs may be
    * derived recursively. Errors are collapsed into the user-facing macro error message, optionally with derivation
    * logs when `debug.LogDerivation` is in implicit scope.
    */
  final def deriveDifferEntryPoint[T: Type](isCascadeDerive: Boolean): Expr[Differ[T]] = {
    val macroName = s"Differ[${displayTypeName[T]}]"
    deriveDifferMIO[T](isCascadeDerive)
      .runToExprOrFail(
        macroName,
        infoRendering = if (shouldWeLogDerivation) RenderFrom(Log.Level.Info) else DontRender,
      ) { (errorLogs, errors) =>
        formatDerivationErrors[T](errorLogs, errors.toVector)
      }
  }

  /** Runs root derivation and closes over all cached lazy vals produced during this derivation.
    *
    * This is the top-level derivation program used by both explicit and auto derivation. It deliberately starts root
    * resolution without summoning an existing implicit, so `Differ.derived[T]` builds a new `Differ[T]` for the root
    * instead of rediscovering the macro currently being expanded.
    */
  private def deriveDifferMIO[T: Type](isCascadeDerive: Boolean): MIO[Expr[Differ[T]]] =
    Log.namedScope(s"Derivation for Differ[${displayTypeName[T]}]") {
      for {
        expr <- resolveRootDiffer[T](isCascadeDerive, summonExisting = false)
        cache <- derivedDiffersCache.get
        closed = cache.toValDefs.use(_ => expr)
      } yield closed
    }

  /** Resolves a Differ for a field.
    *
    * Explicit field differs win before default container support. This lets locally scoped configured differs such as
    * `Differ[List[A]]` participate in product derivation.
    */
  private def resolveFieldDiffer[A: Type](isCascadeDerive: Boolean): MIO[Expr[Differ[A]]] =
    Log.namedScope(s"Resolving field Differ[${displayTypeName[A]}]") {
      resolveCachedDiffer[A](shouldDeclareForwardReference = false) {
        implicit val differAType: Type[Differ[A]] = differType[A]

        MIO(Expr.summonImplicitIgnoring[Differ[A]](ignoredDifferImplicits*).toOption).flatMap {
          case Some(differ) =>
            Log.info(s"Summoned Differ[${displayTypeName[A]}] 👻") >>
              MIO.pure(differ)
          case None =>
            resolveContainer1[A](isCascadeDerive)
              .flatMap {
                case Some(differ) => MIO.pure(Some(differ))
                case None => resolveContainer2[A](isCascadeDerive)
              }
              .flatMap {
                case Some(differ) => MIO.pure(differ)
                case None =>
                  if (isCascadeDerive) resolveRootDiffer[A](isCascadeDerive, summonExisting = false)
                  else MIO.fail(DifferDerivationError.missingDiffer(displayTypeName[A], isCascadeDerive))
              }
        }
      }
    }

  /** Resolve a Differ instance:
    *   - From current derivation cache if it's already cached
    *   - From the implicit scope (only when summonExisting = true)
    *   - Derive it
    *
    * resolveRootDiffer handles a few paths in derivation logic:
    *   - As a starting point for derivation
    *   - Deriving a child case class of a sealed trait
    */
  private def resolveRootDiffer[A: Type](
    isCascadeDerive: Boolean,
    summonExisting: Boolean,
  ): MIO[Expr[Differ[A]]] =
    Log.namedScope(s"Resolve root Differ[${displayTypeName[A]}]") {
      resolveCachedDiffer[A](shouldDeclareForwardReference = true) {
        if (summonExisting) {
          for {
            summonResult <- MIO {
              implicit val differAType: Type[Differ[A]] = differType[A]
              Expr.summonImplicitIgnoring[Differ[A]](ignoredDifferImplicits*).toOption
            }
            differ <- summonResult match {
              case Some(differ) =>
                Log.info(s"Found Differ[${displayTypeName[A]}]") >>
                  MIO.pure(differ)
              case None =>
                deriveDiffer[A](isCascadeDerive)
            }
          } yield differ
        } else {
          deriveDiffer[A](isCascadeDerive)
        }
      }
    }

  /** Checks successful and failed derivation caches before running an uncached resolver.
    *
    * When `shouldDeclareForwardReference` is enabled, this forward-declares the cache entry before running the uncached
    * resolver and fills it with a `LazyDiffer` wrapper afterwards, so recursive product fields and sealed children can
    * refer back to the containing type.
    */
  private def resolveCachedDiffer[A: Type](
    shouldDeclareForwardReference: Boolean,
  )(
    resolveUncached: => MIO[Expr[Differ[A]]],
  ): MIO[Expr[Differ[A]]] = {
    implicit val differAType: Type[Differ[A]] = differType[A]
    val cacheKey = derivationCacheKey[A]

    for {
      failedDiffers <- failedDerivedDiffersCache.get
      result <- failedDiffers.get(cacheKey) match {
        case Some(error) =>
          Log.info(s"Using cached failed derivation for Differ[${displayTypeName[A]}]") >>
            MIO.fail(error)
        case None =>
          derivedDiffersCache.get0Ary[Differ[A]](cacheKey).flatMap {
            case Some(cached) =>
              Log.info(s"Using cached Differ[${displayTypeName[A]}] 📦") >>
                MIO.pure(cached)
            case None =>
              val builder = ValDefBuilder.ofLazy[Differ[A]]("derivedDiffer")
              if (shouldDeclareForwardReference) {

                for {
                  _ <- derivedDiffersCache.forwardDeclare(cacheKey, builder)
                  body <- cacheDerivationFailures[A](resolveUncached)
                  // LazyDiffer is needed in order to support recursive types
                  lazyDifferExpr = Expr.quote {
                    new LazyDiffer[A](Expr.splice(body)): Differ[A]
                  }
                  _ <- derivedDiffersCache.buildCachedWith(cacheKey, builder)(_ => lazyDifferExpr)
                  built <- derivedDiffersCache.get0Ary[Differ[A]](cacheKey)
                } yield built.get
              } else {
                for {
                  differ <- cacheDerivationFailures[A](resolveUncached)
                  cached <- derivedDiffersCache.get0Ary[Differ[A]](cacheKey)
                  result <- cached match {
                    case Some(cached) =>
                      MIO.pure(cached)
                    case None =>
                      for {
                        _ <- derivedDiffersCache.buildCachedWith(cacheKey, builder)(_ => differ)
                        built <- derivedDiffersCache.get0Ary[Differ[A]](cacheKey)
                      } yield built.get
                  }
                } yield result
              }
          }
      }
    } yield result
  }

  private def cacheDerivationFailures[A: Type](
    resolve: => MIO[Expr[Differ[A]]],
  ): MIO[Expr[Differ[A]]] =
    resolve.handleErrorWith { errors =>
      val error = combineErrors(errors.toVector, displayTypeName[A])
      val cacheKey = derivationCacheKey[A]
      failedDerivedDiffersCache.get.flatMap { failed =>
        failedDerivedDiffersCache.set(failed.updated(cacheKey, error)) >>
          MIO.fail(error)
      }
    }

  /** Resolves Differ for any type that has 2 type arguments.
    *
    * Cases covered:
    *   - Option-like types use `Differ.optionDiffer`,
    *   - types with a `SeqLike` instance
    *   - types with a `SetLike` instance
    */
  private def resolveContainer1[A: Type](
    isCascadeDerive: Boolean,
  ): MIO[Option[Expr[Differ[A]]]] =
    MIO(mkCollectionHelper1[A]).flatMap {
      case Some(applied) =>
        import applied.elemType

        reportContainerFailure[A] {
          MIO(optionCtor.unapply(Type[A])).flatMap {
            case Some(_) =>
              Log.info(s"Is Option: ${displayTypeName[A]}") >>
                resolveFieldDiffer[applied.Elem](isCascadeDerive).map { elemDiffer =>
                  Some(optionDiffer[A, applied.Elem](elemDiffer))
                }

            case None =>
              summonExprOpt[SeqLike[applied.F]](applied.typeOfSeqLike) match {
                case Some(seqLike) =>
                  Log.info(s"Found SeqLike instance for ${displayTypeName[A]}. Resolving SeqDiffer..") >>
                    resolveFieldDiffer[applied.Elem](isCascadeDerive).map { elemDiffer =>
                      Some(seqDiffer[A, applied.Elem](elemDiffer, seqLike.asInstanceOf[Expr[Any]]))
                    }
                case None =>
                  summonExprOpt[SetLike[applied.F]](applied.typeOfSetLike) match {
                    case Some(setLike) =>
                      Log.info(s"Found SetLike instance for ${displayTypeName[A]}. Resolving SetDiffer..") >>
                        resolveFieldDiffer[applied.Elem](isCascadeDerive).map { elemDiffer =>
                          Some(setDiffer[A, applied.Elem](elemDiffer, setLike.asInstanceOf[Expr[Any]]))
                        }
                    case None =>
                      MIO.pure(None)
                  }
              }
          }
        }

      case None =>
        MIO.pure(None)
    }

  private def reportContainerFailure[A: Type](
    resolve: MIO[Option[Expr[Differ[A]]]],
  ): MIO[Option[Expr[Differ[A]]]] =
    resolve.handleErrorWith { errors =>
      MIO.fail(DifferDerivationError.FailedDiffer(displayTypeName[A], derivationErrors(errors.toVector)))
    }

  /** Resolves Differ for any type that has 2 type arguments.
    *
    * Cases covered:
    *   - Either
    *   - types with a `MapLike` instance use `MapDiffer` values,
    *   - all other two-argument types fall through to implicit search or derivation.
    */
  private def resolveContainer2[A: Type](
    isCascadeDerive: Boolean,
  ): MIO[Option[Expr[Differ[A]]]] =
    MIO(mkCollectionHelper2[A]).flatMap {
      case Some(applied) =>
        import applied.keyType
        import applied.valueType

        reportContainerFailure[A] {
          MIO(eitherCtor.unapply(Type[A])).flatMap {
            case Some(_) =>
              Log.info(s"Resolving Either-like Differ[${displayTypeName[A]}]") >>
                resolveFieldDiffer[applied.Key](isCascadeDerive)
                  .parMap2(resolveFieldDiffer[applied.Value](isCascadeDerive)) { (leftDiffer, rightDiffer) =>
                    eitherDiffer[A, applied.Key, applied.Value](leftDiffer, rightDiffer)
                  }
                  .map(Some(_))

            case None =>
              summonExprOpt[MapLike[applied.F]](applied.typeOfMapLike) match {
                case Some(mapLike) =>
                  val keyDiffer = MIO(summonExprOpt(valueDifferCtor.apply[applied.Key])).flatMap {
                    case Some(differ) => MIO.pure(differ)
                    case None =>
                      MIO.fail(DifferDerivationError.missingValueDiffer(displayTypeName[applied.Key]))
                  }
                  val valueDiffer =
                    resolveFieldDiffer[applied.Value](isCascadeDerive)

                  Log.info(s"MapLike: Differ[${displayTypeName[A]}]") >>
                    keyDiffer
                      .parMap2(valueDiffer) { (key, value) =>
                        mapDiffer[A, applied.Key, applied.Value](key, value, mapLike.asInstanceOf[Expr[Any]])
                      }
                      .map(Some(_))
                case None =>
                  MIO.pure(None)
              }
          }
        }

      case None =>
        MIO.pure(None)
    }

  /** Derives a Differ for a non-container type.
    *
    * Cases covered, in order:
    *   - case class or case object/value derivation,
    *   - sealed hierarchy derivation,
    *   - missing-differ error for types that are neither products nor sealed hierarchies.
    */
  private def deriveDiffer[A: Type](isCascadeDerive: Boolean): MIO[Expr[Differ[A]]] =
    for {
      _ <- Log.info(s"Deriving Differ[${displayTypeName[A]}]")
      body <- deriveCaseClassOrCaseObject[A](isCascadeDerive)
        .getOrElse(deriveSealedOrMissing[A](isCascadeDerive))
    } yield body

  /** Derivation for case class, case object, or case val.
    *
    * Case classes become `ProductDiffer`s with their primary-constructor fields. Case objects and case vals are
    * singleton values and use equality. Non-product types fall through to sealed-hierarchy derivation.
    */
  private def deriveCaseClassOrCaseObject[A: Type](isCascadeDerive: Boolean): Option[MIO[Expr[Differ[A]]]] =
    if (Type[A].isCaseClass) {
      Type[A].primaryConstructor.map { primaryConstructor =>
        val fields = primaryConstructor.parameters.flatten.toVector.map { case (name, parameter) =>
          name -> parameter.tpe
        }

        deriveProduct[A](fields, isCascadeDerive)
      }
    } else if (Type[A].isCaseObject || Type[A].isCaseVal) {
      Some(deriveSingleton[A])
    } else {
      None
    }

  private def deriveSingleton[A: Type]: MIO[Expr[Differ[A]]] =
    MIO.pure {
      val label = Expr(scala.reflect.NameTransformer.decode(Type[A].shortName).stripSuffix(".type"))
      val typeName = staticTypeName[A]
      Expr.quote {
        val valueToString: Any => String = Function.const(Expr.splice(label))
        new difflicious.differ.EqualsDiffer[A](
          isIgnored = false,
          valueToString = valueToString.asInstanceOf[A => String],
          typeName = Expr.splice(typeName).asInstanceOf[TypeName[A]],
        ): Differ[A]
      }
    }

  /** Derives sealed hierarchies or reports a missing Differ.
    */
  private def deriveSealedOrMissing[A: Type](isCascadeDerive: Boolean): MIO[Expr[Differ[A]]] =
    Type[A].exhaustiveChildren match {
      case Some(children) => deriveSealed[A](children.toVector, isCascadeDerive)
      case None =>
        MIO.fail(DifferDerivationError.missingDiffer(displayTypeName[A], isCascadeDerive))
    }

  /** Builds a ProductDiffer for a case class.
    *
    * Each constructor field is resolved as a field differ, so the field path can use container derivation, existing
    * implicits, or cascade derivation. Field failures are grouped under the product type in the final error tree.
    */
  private def deriveProduct[A: Type](
    fields: Vector[(String, ??)],
    isCascadeDerive: Boolean,
  ): MIO[Expr[Differ[A]]] =
    fields
      .parTraverse { case (fieldName, fieldType) =>
        import fieldType.Underlying

        resolveFieldDiffer[fieldType.Underlying](isCascadeDerive).map { fieldDiffer =>
          val nameExpr = Expr(fieldName)
          Expr.quote {
            (Expr.splice(nameExpr), Expr.splice(fieldDiffer).asInstanceOf[Differ[Any]])
          }
        }
      }
      .handleErrorWith { errors =>
        MIO.fail(DifferDerivationError.FailedDiffer(displayTypeName[A], derivationErrors(errors.toVector)))
      }
      .map { fieldDiffers =>
        {
          implicit val fieldExprType: Type[(String, Differ[Any])] = fieldDifferEntryType
          val fieldsExpr = vectorExpr[(String, Differ[Any])](fieldDiffers)
          val typeName = staticTypeName[A]

          Expr.quote {
            new ProductDiffer[A](
              Expr.splice(fieldsExpr),
              false,
              Expr.splice(typeName),
            ): Differ[A]
          }
        }
      }

  /** Builds a OneOfDiffer for a sealed trait/class/enum.
    *
    * Each exhaustive child is resolved as a root differ with implicit search enabled, so explicit child instances win
    * over child derivation. Child failures are grouped under the sealed parent in the final error tree.
    */
  private def deriveSealed[A: Type](
    children: Vector[(String, ??<:[A])],
    isCascadeDerive: Boolean,
  ): MIO[Expr[Differ[A]]] =
    children
      .parTraverse { case (_, child) =>
        import child.Underlying

        resolveRootDiffer[child.Underlying](
          isCascadeDerive = isCascadeDerive,
          summonExisting = true,
        ).map { childDiffer =>
          mkSealedCase[A, child.Underlying](childDiffer)
        }
      }
      .handleErrorWith { errors =>
        MIO.fail(DifferDerivationError.FailedDiffer(displayTypeName[A], derivationErrors(errors.toVector)))
      }
      .map { cases =>
        {
          implicit val caseExprType: Type[OneOfDiffer.Case[A, Any]] = oneOfCaseAnyType[A]
          val casesExpr = vectorExpr[OneOfDiffer.Case[A, Any]](cases)
          Expr.quote {
            new OneOfDiffer[A](
              Expr.splice(casesExpr),
              false,
              "OneOfDiffer",
            ): Differ[A]
          }
        }
      }

  /** Builds a OneOfDiffer.Case for a sealed trait child.
    */
  private def mkSealedCase[A: Type, B: Type](
    differ: Expr[Differ[B]],
  ): Expr[OneOfDiffer.Case[A, Any]] = {
    val typeName = staticTypeName[B]
    val extract = extractCase[A, B]

    Expr.quote {
      OneOfDiffer
        .caseOf[A, B](
          Expr.splice(typeName),
          Expr.splice(extract),
          Expr.splice(differ),
        )
        .asInstanceOf[OneOfDiffer.Case[A, Any]]
    }
  }

  /** Builds the runtime extractor used by OneOfDiffer cases.
    *
    * Singleton children, such as case objects and enum cases, are matched by equality against their singleton value.
    */
  @nowarn("msg=abstract type .* is unchecked since it is eliminated by erasure")
  private def extractCase[A: Type, B: Type]: Expr[A => Option[B]] =
    Expr.singletonOf[B] match {
      case Some(singleton) =>
        Expr.quote { (value: A) =>
          if (value == Expr.splice(singleton)) Some(Expr.splice(singleton))
          else None
        }
      case None =>
        Expr.quote { (value: A) =>
          if (value.isInstanceOf[B]) Some(value.asInstanceOf[B])
          else None
        }
    }

  /** Builds the Option differ expression for an Option-like applied type. */
  private def optionDiffer[A: Type, Elem: Type](
    valueDiffer: Expr[Differ[Elem]],
  ): Expr[Differ[A]] =
    Expr.quote {
      Differ.optionDiffer[Elem](using Expr.splice(valueDiffer)).asInstanceOf[Differ[A]]
    }

  /** Builds the Either differ expression for an Either-like applied type. */
  private def eitherDiffer[A: Type, L: Type, R: Type](
    leftDiffer: Expr[Differ[L]],
    rightDiffer: Expr[Differ[R]],
  ): Expr[Differ[A]] =
    Expr.quote {
      Differ
        .eitherDiffer[L, R](using
          Expr.splice(leftDiffer),
          Expr.splice(rightDiffer),
        )
        .asInstanceOf[Differ[A]]
    }

  /** Builds the Seq differ expression for any one-argument type with a SeqLike instance. */
  private def seqDiffer[A: Type, Elem: Type](
    itemDiffer: Expr[Differ[Elem]],
    seqLike: Expr[Any],
  ): Expr[Differ[A]] = {
    val typeName = staticTypeName[A]

    Expr.quote {
      type Container[X] = A

      difflicious.differ.SeqDiffer
        .create[Container, Elem](
          itemDiffer = Expr.splice(itemDiffer),
          typeName = Expr.splice(typeName),
          asSeq = Expr.splice(seqLike).asInstanceOf[difflicious.utils.SeqLike[Container]],
        )
    }
  }

  /** Builds the Set differ expression for any one-argument type with a SetLike instance. */
  private def setDiffer[A: Type, Elem: Type](
    itemDiffer: Expr[Differ[Elem]],
    setLike: Expr[Any],
  ): Expr[Differ[A]] = {
    val typeName = staticTypeName[A]

    Expr.quote {
      type Container[X] = A

      difflicious.differ.SetDiffer
        .create[Container, Elem](
          itemDiffer = Expr.splice(itemDiffer),
          typeName = Expr.splice(typeName),
          asSet = Expr.splice(setLike).asInstanceOf[difflicious.utils.SetLike[Container]],
        )
    }
  }

  /** Builds the Map differ expression for any two-argument type with a MapLike instance. */
  private def mapDiffer[A: Type, Key: Type, Value: Type](
    keyDiffer: Expr[ValueDiffer[Key]],
    valueDiffer: Expr[Differ[Value]],
    asMap: Expr[Any],
  ): Expr[Differ[A]] = {
    val typeName = staticTypeName[A]

    Expr.quote {
      type Container[X, Y] = A

      new difflicious.differ.MapDiffer[Container, Key, Value](
        isIgnored = false,
        keyDiffer = Expr.splice(keyDiffer),
        valueDiffer = Expr.splice(valueDiffer),
        typeName = Expr.splice(typeName),
        asMap = Expr.splice(asMap).asInstanceOf[difflicious.utils.MapLike[Container]],
      )
    }
  }

  protected def staticTypeName[A: Type]: Expr[TypeName.SomeTypeName]

  private def summonExprOpt[A](instanceType: Type[A]): Option[Expr[A]] = {
    implicit val implicitType: Type[A] = instanceType
    Expr.summonImplicit[A].toOption
  }

  /** Converts generated element expressions into a Vector expression without relying on varargs quoting. */
  private def vectorExpr[A: Type](items: Vector[Expr[A]]): Expr[Vector[A]] =
    items.foldLeft(Expr.quote(Vector.empty[A])) { (acc, item) =>
      Expr.quote(Expr.splice(acc) :+ Expr.splice(item))
    }

  /** Normalizes thrown errors into unique DifferDerivationError values. */
  private def derivationErrors(errors: Vector[Throwable]): Vector[DifferDerivationError] =
    errors.distinct.map {
      case error: DifferDerivationError => error
      case error => DifferDerivationError.Other(Option(error.getMessage).getOrElse(error.toString))
    }

  /** Collapses one or more errors into the most specific error tree for a parent differ. */
  private def combineErrors(errors: Vector[Throwable], differType: String): DifferDerivationError =
    derivationErrors(errors) match {
      case Vector(error) => error
      case errors => DifferDerivationError.FailedDiffer(differType, errors)
    }

  /** Short, decoded type name used in logs and user-facing derivation errors. */
  private def displayTypeName[A: Type]: String =
    scala.reflect.NameTransformer.decode(Type[A].prettyPrint)

  /** Formats macro failure output, optionally appending derivation logs captured by hearth. */
  private def formatDerivationErrors[A: Type](errorLogs: String, errors: Vector[Throwable]): String = {
    val combinedError = combineErrors(errors, displayTypeName[A])

    val tree = renderError(combinedError, indent = 0, root = true)
    val missingInstances = collectMissingInstances(combinedError)
    val missingOnly = missingInstances.collect {
      case missing if !missing.canDerive => missing.instanceRepr
    }.distinct
    val missingOrDerived = missingInstances.collect {
      case missing if missing.canDerive => missing.instanceRepr
    }.distinct
    val summaryParts = Vector(
      Option.when(missingOnly.nonEmpty) {
        s"couldn't find ${missingOnly.mkString(", ")}"
      },
      Option.when(missingOrDerived.nonEmpty) {
        s"couldn't find or derive ${missingOrDerived.mkString(", ")}"
      },
    ).flatten
    val summary =
      if (summaryParts.isEmpty) ""
      else s"\n\nSummary: Derivation failed because we ${summaryParts.mkString(" and ")}"

    val message = s"Failed to derive Differ[${displayTypeName[A]}]\n\n$tree$summary"

    if (errorLogs.trim.isEmpty) message
    else s"$message\n\nDerivation error logs:\n$errorLogs"
  }

  /** Renders a nested derivation error tree.
    *
    * Failed product/sealed/container nodes render as branch headers; missing or other errors render as leaves.
    */
  private def renderError(
    error: DifferDerivationError,
    indent: Int,
    root: Boolean,
  ): String = {
    val prefix = "  ".repeat(indent)

    error match {
      case DifferDerivationError.FailedDiffer(differType, errors) =>
        val header =
          if (root) s"$prefix$differType"
          else s"$prefix${error.getMessage}"
        val children = errors.distinct.map(renderError(_, indent + 1, root = false))
        (header +: children).mkString("\n")
      case _ =>
        s"$prefix${error.getMessage}"
    }
  }

  /** Collects all missing instance leaves from an error tree for the final summary line. */
  private def collectMissingInstances(
    error: DifferDerivationError,
  ): Vector[DifferDerivationError.MissingInstance] =
    error match {
      case missing: DifferDerivationError.MissingInstance => Vector(missing)
      case DifferDerivationError.FailedDiffer(_, errors) =>
        errors.flatMap(collectMissingInstances)
      case DifferDerivationError.Other(_) =>
        Vector.empty
    }

  /** Helper for Collections with 1 type parameter. Helper us construct certain Type[..] values that involves higher
    * kinded types (not supported by Hearth atm).
    */
  protected trait CollectionHelper1[A] {
    type F[_]
    type Elem

    implicit val elemType: Type[Elem]

    def typeOfSeqLike: Type[SeqLike[F]]
    def typeOfSetLike: Type[SetLike[F]]
  }

  /** Helper for Collections with 2 type parameter. Helper us construct certain Type[..] values that involves higher
    * kinded types (not supported by Hearth atm).
    */
  protected trait CollectionHelper2[A] {
    type F[_, _]
    type Key
    type Value

    implicit val keyType: Type[Key]
    implicit val valueType: Type[Value]

    def typeOfMapLike: Type[MapLike[F]]
  }

}

private[difflicious] sealed abstract class DifferDerivationError(val instanceRepr: String)
    extends RuntimeException
    with NoStackTrace {
  protected def message: String

  override def getMessage: String = message
}

private[difflicious] object DifferDerivationError {
  final case class Other(reason: String) extends DifferDerivationError(reason) {
    protected def message: String = reason
  }

  def missingDiffer(differType: String, canDerive: Boolean): MissingInstance =
    MissingInstance(s"Differ[$differType]", canDerive)

  def missingValueDiffer(differType: String): MissingInstance =
    MissingInstance(s"ValueDiffer[$differType]", canDerive = false)

  private def missingInstanceMessage(canDerive: Boolean): String =
    if (canDerive) "cannot be found or derived"
    else "cannot be found"

  final case class MissingInstance(
    override val instanceRepr: String,
    canDerive: Boolean,
  ) extends DifferDerivationError(instanceRepr) {
    protected def message: String =
      s"$instanceRepr ${missingInstanceMessage(canDerive)}"
  }

  final case class FailedDiffer(
    differType: String,
    errors: Vector[DifferDerivationError],
  ) extends DifferDerivationError(s"Differ[$differType]") {
    protected def message: String =
      s"$instanceRepr cannot be derived because..."
  }
}

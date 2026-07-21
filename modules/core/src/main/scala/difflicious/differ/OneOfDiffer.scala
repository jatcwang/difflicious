package difflicious.differ

import difflicious.*
import difflicious.DiffResult.MismatchTypeResult
import difflicious.utils.TypeName.SomeTypeName

final class OneOfDiffer[A](
  cases: Vector[OneOfDiffer.Case[A, ?]],
  isIgnored: Boolean,
  differTypeName: String,
  canUseEqualsValue: => Boolean,
) extends Differ[A] {
  import OneOfDiffer.*

  private val caseIds = cases.map(_.id)
  require(cases.nonEmpty, "OneOfDiffer requires at least one case")
  require(
    caseIds.distinct.size == caseIds.size,
    s"OneOfDiffer case ids must be distinct. Found case ids: ${caseIds.mkString(",")}",
  )

  override type R = DiffResult
  override lazy val canUseEquals: Boolean = canUseEqualsValue

  override def diff(inputs: DiffInput[A]): DiffResult = inputs match {
    case DiffInput.ObtainedOnly(obtained) =>
      val obtainedCase = selectCaseForValue(obtained)
      applyCaseToDiffInput(obtainedCase, DiffInput.ObtainedOnly(obtained))
    case DiffInput.ExpectedOnly(expected) =>
      val expectedCase = selectCaseForValue(expected)
      applyCaseToDiffInput(expectedCase, DiffInput.ExpectedOnly(expected))
    case DiffInput.Both(obtained, expected) =>
      val obtainedCase = selectCaseForValue(obtained)
      val expectedCase = selectCaseForValue(expected)
      if (obtainedCase.id == expectedCase.id) {
        applyCaseToDiffInput(obtainedCase, DiffInput.Both(obtained, expected))
      } else {
        MismatchTypeResult(
          obtained = applyCaseToDiffInput(obtainedCase, DiffInput.ObtainedOnly(obtained)),
          obtainedTypeName = obtainedCase.typeName,
          expected = applyCaseToDiffInput(expectedCase, DiffInput.ExpectedOnly(expected)),
          expectedTypeName = expectedCase.typeName,
          pairType = PairType.Both,
          isIgnored = isIgnored,
        )
      }
  }

  override def configureIgnored(newIgnored: Boolean): Differ[A] =
    new OneOfDiffer[A](
      cases = cases.map(_.configureRawOrThrow(ConfigurePath.current, ConfigureOp.SetIgnored(newIgnored))),
      isIgnored = newIgnored,
      differTypeName = differTypeName,
      canUseEqualsValue = false,
    )

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[A]] = {
    cases.zipWithIndex.find { case (caseDef, _) => caseDef.id == step } match {
      case Some((caseDef, idx)) =>
        caseDef.configureRaw(nextPath, op).map { updatedCase =>
          new OneOfDiffer[A](
            cases = cases.updated(idx, updatedCase),
            isIgnored = isIgnored,
            differTypeName = differTypeName,
            canUseEqualsValue = false,
          )
        }
      case None =>
        Left(ConfigureError.UnrecognizedSubType(nextPath, caseIds))
    }
  }

  override def configurePairBy(path: ConfigurePath, op: ConfigureOp.PairBy[?]): Either[ConfigureError, Differ[A]] =
    Left(ConfigureError.InvalidConfigureOp(path, op, differTypeName))

  private def selectCaseForValue(value: A): Case[A, ?] =
    cases.iterator
      .find(_.extract(value).isDefined)
      .getOrElse {
        throw new IllegalStateException(
          s"OneOfDiffer could not match value to any configured case. Configured cases: ${caseIds.mkString(",")}",
        )
      }

  private def applyCaseToDiffInput(caseDef: Case[A, ?], inputs: DiffInput[A]): DiffResult =
    caseDef.diff(inputs)
}

object OneOfDiffer {

  /** A single possible case in a OneOfDiffer.
    *
    * A typeName is required because we want to provide an alternative name. For example, a JSON array may be
    * stored/compared as a List, but we want to call it JsonArray
    */
  final case class Case[A, B](
    typeName: SomeTypeName,
    extract: A => Option[B],
    differ: Differ[B],
  ) {
    def id: String = typeName.short

    def canUseEquals: Boolean = differ.canUseEquals

    private[difflicious] def diff(inputs: DiffInput[A]): DiffResult =
      differ.diff(
        inputs.map { value =>
          extract(value).getOrElse {
            throw new IllegalStateException(s"Value does not match selected OneOfDiffer case $id")
          }
        },
      )

    private[difflicious] def configureRaw(
      path: ConfigurePath,
      op: ConfigureOp,
    ): Either[ConfigureError, Case[A, B]] =
      differ.configureRaw(path, op).map { updatedDiffer =>
        copy(differ = updatedDiffer)
      }

    private[difflicious] def configureRawOrThrow(path: ConfigurePath, op: ConfigureOp): Case[A, B] =
      configureRaw(path, op) match {
        case Right(updatedCase) => updatedCase
        case Left(error) => throw error
      }
  }

  def caseOf[A, B](
    typeName: SomeTypeName,
    extract: A => Option[B],
    differ: Differ[B],
  ): Case[A, B] =
    Case(
      typeName = typeName,
      extract = extract,
      differ = differ,
    )
}

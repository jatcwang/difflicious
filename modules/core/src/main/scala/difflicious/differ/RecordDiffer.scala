package difflicious.differ

import scala.collection.immutable.ListMap
import difflicious._
import difflicious.internal.SumCountsSyntax.DiffResultIterableOps
import difflicious.utils.TypeName.SomeTypeName

/** A differ for a record-like data structure such as tuple or case classes.
  */
final class RecordDiffer[T](
  fieldDiffers: ListMap[String, (T => Any, Differ[Any])],
  isIgnored: Boolean,
  typeName: SomeTypeName,
) extends Differ[T] {
  override type R = DiffResult.RecordResult

  override def diff(inputs: DiffInput[T]): R = inputs match {
    case DiffInput.Both(obtained, expected) => {
      val diffResults = fieldDiffers
        .map { case (fieldName, (getter, differ)) =>
          val diffResult = differ.diff(getter(obtained), getter(expected))

          fieldName -> diffResult
        }
        .to(ListMap)

      val diffResultValues = diffResults.values
      DiffResult
        .RecordResult(
          typeName = typeName,
          fields = diffResults,
          pairType = PairType.Both,
          isIgnored = isIgnored,
          isOk = isIgnored || diffResultValues.forall(_.isOk),
          differenceCount = diffResultValues.differenceCount,
          ignoredCount = diffResultValues.ignoredCount,
        )
    }
    case DiffInput.ObtainedOnly(value) => {
      val diffResults = fieldDiffers
        .map { case (fieldName, (getter, differ)) =>
          val diffResult = differ.diff(DiffInput.ObtainedOnly(getter(value)))

          fieldName -> diffResult
        }
        .to(ListMap)
      DiffResult
        .RecordResult(
          typeName = typeName,
          fields = diffResults,
          pairType = PairType.ObtainedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
          differenceCount = diffResults.values.size,
          ignoredCount = if (isIgnored) diffResults.values.size else 0,
        )
    }
    case DiffInput.ExpectedOnly(expected) => {
      val diffResults = fieldDiffers
        .map { case (fieldName, (getter, differ)) =>
          val diffResult = differ.diff(DiffInput.ExpectedOnly(getter(expected)))

          fieldName -> diffResult
        }
        .to(ListMap)
      DiffResult
        .RecordResult(
          typeName = typeName,
          fields = diffResults,
          pairType = PairType.ExpectedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
          differenceCount = diffResults.values.size,
          ignoredCount = if (isIgnored) diffResults.values.size else 0,
        )
    }
  }

  override def configureIgnored(newIgnored: Boolean): Differ[T] =
    new RecordDiffer[T](fieldDiffers = fieldDiffers, isIgnored = newIgnored, typeName = typeName)

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[T]] =
    fieldDiffers
      .get(step)
      .toRight(ConfigureError.NonExistentField(nextPath, "RecordDiffer"))
      .flatMap { case (getter, fieldDiffer) =>
        fieldDiffer.configureRaw(nextPath, op).map { newFieldDiffer =>
          new RecordDiffer[T](
            fieldDiffers = fieldDiffers.updated(step, (getter, newFieldDiffer)),
            isIgnored = isIgnored,
            typeName = typeName,
          )
        }
      }
  override def configurePairBy(path: ConfigurePath, op: ConfigureOp.PairBy[_]): Either[ConfigureError, Differ[T]] =
    Left(ConfigureError.InvalidConfigureOp(path, op, "RecordDiffer"))
}

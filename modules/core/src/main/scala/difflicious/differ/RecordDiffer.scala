package difflicious.differ

import scala.collection.immutable.ListMap
import difflicious.utils.TypeName
import difflicious._
import izumi.reflect.macrortti.LTag

/**
  * A differ for a record-like data structure such as tuple or case classes.
  */
final class RecordDiffer[T](
  fieldDiffers: ListMap[String, (T => Any, Differ[Any])],
  isIgnored: Boolean,
  override protected val tag: LTag[T],
  typeName: TypeName,
) extends Differ[T] {
  override type R = DiffResult.RecordResult

  override def diff(inputs: DiffInput[T]): R = inputs match {
    case DiffInput.Both(obtained, expected) => {
      val diffResults = fieldDiffers
        .map {
          case (fieldName, (getter, differ)) =>
            val diffResult = differ.diff(getter(obtained), getter(expected))

            fieldName -> diffResult
        }
        .to(ListMap)
      DiffResult
        .RecordResult(
          typeName = typeName,
          fields = diffResults,
          pairType = PairType.Both,
          isIgnored = isIgnored,
          isOk = isIgnored || diffResults.values.forall(_.isOk),
        )
    }
    case DiffInput.ObtainedOnly(value) => {
      val diffResults = fieldDiffers
        .map {
          case (fieldName, (getter, differ)) =>
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
        )
    }
    case DiffInput.ExpectedOnly(expected) => {
      val diffResults = fieldDiffers
        .map {
          case (fieldName, (getter, differ)) =>
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
        )
    }
  }

  override def configureIgnored(newIgnored: Boolean): Differ[T] =
    new RecordDiffer[T](fieldDiffers = fieldDiffers, isIgnored = newIgnored, typeName = typeName, tag = tag)

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[T]] =
    for {
      (getter, fieldDiffer) <- fieldDiffers
        .get(step)
        .toRight(ConfigureError.NonExistentField(nextPath, "RecordDiffer"))
      newFieldDiffer <- fieldDiffer.configureRaw(nextPath, op)
    } yield new RecordDiffer[T](
      fieldDiffers = fieldDiffers.updated(step, (getter, newFieldDiffer)),
      isIgnored = isIgnored,
      typeName = typeName,
      tag = tag,
    )

  override def configurePairBy(path: ConfigurePath, op: ConfigureOp.PairBy[_]): Either[ConfigureError, Differ[T]] =
    Left(ConfigureError.InvalidConfigureOp(path, op, "RecordDiffer"))
}

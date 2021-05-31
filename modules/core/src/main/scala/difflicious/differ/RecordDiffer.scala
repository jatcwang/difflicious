package difflicious.differ

import scala.collection.immutable.ListMap
import difflicious.utils.TypeName
import difflicious._

final class RecordDiffer[T](
  fieldDiffers: ListMap[String, (T => Any, Differ[Any])],
  isIgnored: Boolean,
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
          matchType = MatchType.Both,
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
          matchType = MatchType.ObtainedOnly,
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
          matchType = MatchType.ExpectedOnly,
          isIgnored = isIgnored,
          isOk = isIgnored,
        )
    }
  }

  override def configureRaw(path: ConfigurePath, op: ConfigureOp): Either[DifferUpdateError, RecordDiffer[T]] = {
    val (step, nextPath) = path.next
    step match {
      case Some(fieldName) =>
        for {
          (getter, fieldDiffer) <- fieldDiffers
            .get(fieldName)
            .toRight(DifferUpdateError.NonExistentField(nextPath, fieldName))
          newFieldDiffer <- fieldDiffer.configureRaw(nextPath, op)
        } yield new RecordDiffer[T](
          fieldDiffers = fieldDiffers.updated(fieldName, (getter, newFieldDiffer)),
          isIgnored = this.isIgnored,
          typeName = typeName,
        )
      case None =>
        op match {
          case ConfigureOp.SetIgnored(newIgnored) =>
            Right(new RecordDiffer[T](fieldDiffers = fieldDiffers, isIgnored = newIgnored, typeName = typeName))
          case _: ConfigureOp.PairBy[_] => Left(DifferUpdateError.InvalidDifferOp(nextPath, op, "record"))
        }

    }
  }
}

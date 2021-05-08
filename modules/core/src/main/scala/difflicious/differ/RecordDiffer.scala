package difflicious.differ

import cats.data.Ior

import scala.collection.immutable.ListMap
import difflicious.utils.TypeName
import difflicious._

final class RecordDiffer[T](
  fieldDiffers: ListMap[String, (T => Any, Differ[Any])],
  isIgnored: Boolean,
  typeName: TypeName,
) extends Differ[T] {
  override type R = DiffResult.RecordResult

  override def diff(inputs: Ior[T, T]): R = inputs match {
    case Ior.Both(obtained, expected) => {
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
    case Ior.Left(value) => {
      val diffResults = fieldDiffers
        .map {
          case (fieldName, (getter, differ)) =>
            val diffResult = differ.diff(Ior.left(getter(value)))

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
    case Ior.Right(expected) => {
      val diffResults = fieldDiffers
        .map {
          case (fieldName, (getter, differ)) =>
            val diffResult = differ.diff(Ior.Right(getter(expected)))

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

  override def updateWith(path: UpdatePath, op: DifferOp): Either[DifferUpdateError, RecordDiffer[T]] = {
    val (step, nextPath) = path.next
    step match {
      case Some(UpdateStep.DownPath(fieldName)) =>
        for {
          (getter, fieldDiffer) <- fieldDiffers
            .get(fieldName)
            .toRight(DifferUpdateError.NonExistentField(nextPath, fieldName))
          newFieldDiffer <- fieldDiffer.updateWith(nextPath, op)
        } yield new RecordDiffer[T](
          fieldDiffers = fieldDiffers.updated(fieldName, (getter, newFieldDiffer)),
          isIgnored = this.isIgnored,
          typeName = typeName,
        )
      case None =>
        op match {
          case DifferOp.SetIgnored(newIgnored) =>
            Right(new RecordDiffer[T](fieldDiffers = fieldDiffers, isIgnored = newIgnored, typeName = typeName))
          case _: DifferOp.MatchBy[_] => Left(DifferUpdateError.InvalidDifferOp(nextPath, op, "record"))
        }

    }
  }

  def ignoreFieldByNameOrFail(fieldName: String): RecordDiffer[T] =
    updateWith(UpdatePath.of(UpdateStep.DownPath(fieldName)), DifferOp.SetIgnored(true)) match {
      case Left(_) =>
        throw new IllegalArgumentException(s"Cannot ignore field: field '$fieldName' is not part of record")
      case Right(differ) => differ
    }
}

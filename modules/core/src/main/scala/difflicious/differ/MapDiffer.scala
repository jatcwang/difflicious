package difflicious.differ

import difflicious.DiffResult.{ValueResult, MapResult}

import scala.collection.mutable
import difflicious.ConfigureOp.PairBy
import difflicious.differ.MapDiffer.mapKeyToString
import izumi.reflect.macrortti.LTag
import difflicious.{PairType, Differ, DiffResult, ConfigureOp, ConfigureError, ConfigurePath, DiffInput}
import difflicious.utils.{TypeName, MapLike}

class MapDiffer[M[_, _], K, V](
  isIgnored: Boolean,
  keyDiffer: ValueDiffer[K],
  valueDiffer: Differ[V],
  val tag: LTag[M[K, V]],
  valueTag: LTag[V],
  typeName: TypeName,
  asMap: MapLike[M],
) extends Differ[M[K, V]] {
  override type R = MapResult

  override def diff(inputs: DiffInput[M[K, V]]): R = inputs.map(asMap.asMap) match {
    case DiffInput.Both(obtained, expected) =>
      val obtainedOnly = mutable.ArrayBuffer.empty[MapResult.Entry]
      val both = mutable.ArrayBuffer.empty[MapResult.Entry]
      val expectedOnly = mutable.ArrayBuffer.empty[MapResult.Entry]
      obtained.foreach {
        case (k, actualV) =>
          expected.get(k) match {
            case Some(expectedV) =>
              both += MapResult.Entry(
                mapKeyToString(k, keyDiffer),
                valueDiffer.diff(actualV, expectedV),
              )
            case None =>
              obtainedOnly += MapResult.Entry(
                mapKeyToString(k, keyDiffer),
                valueDiffer.diff(DiffInput.ObtainedOnly(actualV)),
              )
          }
      }
      expected.foreach {
        case (k, expectedV) =>
          if (obtained.contains(k)) {
            // Do nothing, already compared when iterating through obtained
          } else {
            expectedOnly += MapResult.Entry(
              mapKeyToString(k, keyDiffer),
              valueDiffer.diff(DiffInput.ExpectedOnly(expectedV)),
            )
          }
      }
      (obtainedOnly ++ both ++ expectedOnly).toVector
      MapResult(
        typeName = typeName,
        (obtainedOnly ++ both ++ expectedOnly).toVector,
        PairType.Both,
        isIgnored = isIgnored,
        isOk = isIgnored || obtainedOnly.isEmpty && expectedOnly.isEmpty && both.forall(_.value.isOk),
      )
    case DiffInput.ObtainedOnly(obtained) =>
      DiffResult.MapResult(
        typeName = typeName,
        entries = obtained.map {
          case (k, v) =>
            MapResult.Entry(mapKeyToString(k, keyDiffer), valueDiffer.diff(DiffInput.ObtainedOnly(v)))
        }.toVector,
        pairType = PairType.ObtainedOnly,
        isIgnored = isIgnored,
        isOk = isIgnored,
      )
    case DiffInput.ExpectedOnly(expected) =>
      DiffResult.MapResult(
        typeName = typeName,
        entries = expected.map {
          case (k, v) =>
            MapResult.Entry(mapKeyToString(k, keyDiffer), valueDiffer.diff(DiffInput.ExpectedOnly(v)))
        }.toVector,
        pairType = PairType.ExpectedOnly,
        isIgnored = isIgnored,
        isOk = isIgnored,
      )
  }

  override def configureIgnored(newIgnored: Boolean): Differ[M[K, V]] = {
    new MapDiffer[M, K, V](
      isIgnored = newIgnored,
      keyDiffer = keyDiffer,
      valueDiffer = valueDiffer,
      tag = tag,
      valueTag = valueTag,
      typeName = typeName,
      asMap = asMap,
    )
  }

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[M[K, V]]] = {
    if (step == "each") {
      valueDiffer.configureRaw(nextPath, op).map { newValueDiffer =>
        new MapDiffer[M, K, V](
          isIgnored = isIgnored,
          keyDiffer = keyDiffer,
          valueDiffer = newValueDiffer,
          tag = tag,
          valueTag = valueTag,
          typeName = typeName,
          asMap = asMap,
        )
      }
    } else
      Left(ConfigureError.NonExistentField(path = nextPath, "MapDiffer"))
  }

  override def configurePairBy(path: ConfigurePath, op: PairBy[_]): Either[ConfigureError, Differ[M[K, V]]] =
    Left(ConfigureError.InvalidConfigureOp(path, op, "MapDiffer"))

}

object MapDiffer {

  private[MapDiffer] def mapKeyToString[T](k: T, keyDiffer: ValueDiffer[T]): String = {
    keyDiffer.diff(DiffInput.ObtainedOnly(k)) match {
      case r: ValueResult.ObtainedOnly => r.obtained
      // $COVERAGE-OFF$
      case r: ValueResult.Both         => r.obtained
      case r: ValueResult.ExpectedOnly => r.expected
      // $COVERAGE-ON$
    }
  }

}

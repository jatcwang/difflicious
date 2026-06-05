package difflicious.differ

import difflicious.*
import difflicious.utils.TypeName.SomeTypeName

import scala.collection.immutable.ListMap

/** A differ for product data structures such as tuples and case classes.
  */
final class ProductDiffer[T](
  fieldDiffers: Vector[(String, Differ[Any])],
  isIgnored: Boolean,
  typeName: SomeTypeName,
) extends Differ[T] {
  override type R = DiffResult.RecordResult

  override def diff(inputs: DiffInput[T]): R = inputs match {
    case DiffInput.Both(obtained, expected) =>
      val obtainedProduct = obtained.asInstanceOf[Product]
      val expectedProduct = expected.asInstanceOf[Product]
      val diffResultsBuilder = ListMap.newBuilder[String, DiffResult]
      var isOk = true
      var index = 0
      val fieldCount = fieldDiffers.size

      while (index < fieldCount) {
        val fieldDiffer = fieldDiffers(index)
        val diffResult =
          fieldDiffer._2.diff(obtainedProduct.productElement(index), expectedProduct.productElement(index))
        diffResultsBuilder += fieldDiffer._1 -> diffResult
        if (!diffResult.isOk) isOk = false
        index += 1
      }

      val diffResults = diffResultsBuilder.result()
      DiffResult
        .RecordResult(
          typeName = typeName,
          fields = diffResults,
          pairType = PairType.Both,
          isIgnored = isIgnored,
          isOk = isIgnored || isOk,
        )
    case DiffInput.ObtainedOnly(value) =>
      calcDiffResultForOneSide(
        product = value.asInstanceOf[Product],
        pairType = PairType.ObtainedOnly,
      )
    case DiffInput.ExpectedOnly(expected) =>
      calcDiffResultForOneSide(
        product = expected.asInstanceOf[Product],
        pairType = PairType.ExpectedOnly,
      )
  }

  private def calcDiffResultForOneSide(
    product: Product,
    pairType: PairType.ObtainedOrExpected,
  ): R = {
    val diffResultsBuilder = ListMap.newBuilder[String, DiffResult]
    var index = 0
    val fieldCount = fieldDiffers.size

    while (index < fieldCount) {
      val fieldDiffer = fieldDiffers(index)
      val fieldValue = product.productElement(index)
      val diffInput: DiffInput[Any] = pairType match {
        case PairType.ObtainedOnly => DiffInput.ObtainedOnly(fieldValue)
        case PairType.ExpectedOnly => DiffInput.ExpectedOnly(fieldValue)
      }
      val diffResult = fieldDiffer._2.diff(diffInput)
      diffResultsBuilder += fieldDiffer._1 -> diffResult
      index += 1
    }

    DiffResult
      .RecordResult(
        typeName = typeName,
        fields = diffResultsBuilder.result(),
        pairType = pairType,
        isIgnored = isIgnored,
        isOk = isIgnored,
      )
  }

  override def configureIgnored(newIgnored: Boolean): Differ[T] =
    new ProductDiffer[T](fieldDiffers = fieldDiffers, isIgnored = newIgnored, typeName = typeName)

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[T]] = {
    val index = fieldDiffers.indexWhere(_._1 == step)

    if (index < 0) {
      Left(ConfigureError.NonExistentField(nextPath, "ProductDiffer"))
    } else {
      val (fieldName, fieldDiffer) = fieldDiffers(index)
      fieldDiffer.configureRaw(nextPath, op).map { newFieldDiffer =>
        new ProductDiffer[T](
          fieldDiffers = fieldDiffers.updated(index, (fieldName, newFieldDiffer)),
          isIgnored = isIgnored,
          typeName = typeName,
        )
      }
    }
  }

  override def configurePairBy(path: ConfigurePath, op: ConfigureOp.PairBy[?]): Either[ConfigureError, Differ[T]] =
    Left(ConfigureError.InvalidConfigureOp(path, op, "ProductDiffer"))
}

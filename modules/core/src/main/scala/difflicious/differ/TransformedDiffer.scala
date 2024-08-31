package difflicious.differ

import difflicious.{ConfigureError, ConfigureOp, ConfigurePath, DiffInput, DiffResult}

/** A Differ that transforms any input of diff method and pass it to its underlying Differ. See
  * [[ValueDiffer.contramap]]
  */
class TransformedDiffer[T, U](underlyingDiffer: ValueDiffer[U], transformFunc: T => U) extends ValueDiffer[T] {
  override def diff(inputs: DiffInput[T]): DiffResult.ValueResult = underlyingDiffer.diff(inputs.map(transformFunc))

  override def configureIgnored(newIgnored: Boolean): TransformedDiffer[T, U] = {
    new TransformedDiffer[T, U](
      underlyingDiffer.configureIgnored(newIgnored),
      transformFunc,
    )
  }

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, TransformedDiffer[T, U]] = {
    underlyingDiffer.configurePath(step, nextPath, op).map { newUnderlyingDiffer =>
      new TransformedDiffer(
        newUnderlyingDiffer,
        transformFunc,
      )
    }
  }

  override def configurePairBy(
    path: ConfigurePath,
    op: ConfigureOp.PairBy[_],
  ): Either[ConfigureError, TransformedDiffer[T, U]] = {
    underlyingDiffer.configurePairBy(path, op).map { newUnderlyingDiffer =>
      new TransformedDiffer(
        newUnderlyingDiffer,
        transformFunc,
      )
    }
  }
}

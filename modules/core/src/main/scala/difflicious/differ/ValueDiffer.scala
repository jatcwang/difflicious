package difflicious.differ

import difflicious.{Differ, DiffResult, ConfigureOp, ConfigureError, ConfigurePath, DiffInput}

/**
  * Differ where the error diagnostic output is just string values.
  * Simple types where a string representation is enough for diagnostics purposes
  * should use Differ.useEquals (EqualsDiffer is a subtype of this trait).
  * For example, Differ for Int, String, java.time.Instant are all ValueDiffers.
  *
  * This trait also provide an extra `contramap` method which makes it easy to
  * write instances for newtypes / opaque types.
  */
trait ValueDiffer[T] extends Differ[T] {
  final override type R = DiffResult.ValueResult

  override def diff(inputs: DiffInput[T]): R

  override def configureIgnored(newIgnored: Boolean): ValueDiffer[T]

  override def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, ValueDiffer[T]]

  override def configurePairBy(
    path: ConfigurePath,
    op: ConfigureOp.PairBy[_],
  ): Either[ConfigureError, ValueDiffer[T]]

  final def contramap[S](transformFunc: S => T): TransformedDiffer[S, T] = {
    new TransformedDiffer(this, transformFunc)
  }
}

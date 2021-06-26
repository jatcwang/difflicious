package difflicious.differ

import difflicious.{Differ, DiffResult, ConfigureOp, ConfigureError, ConfigurePath, DiffInput}

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

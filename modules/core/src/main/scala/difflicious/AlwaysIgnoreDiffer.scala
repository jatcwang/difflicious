package difflicious

import difflicious.DiffResult.ValueResult

/** A Differ that always return an Ignored result.
  * Useful when you can't really diff a type */
final class AlwaysIgnoreDiffer[T] extends Differ[T] {
  override type R = ValueResult

  override def diff(inputs: DiffInput[T]): ValueResult =
    ValueResult.Both("[ALWAYS IGNORED]", "[ALWAYS IGNORED]", isSame = true, isIgnored = true)

  override protected def configureIgnored(newIgnored: Boolean): Differ[T] = this

  override protected def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[T]] = Left(ConfigureError.PathTooLong(nextPath))

  override protected def configurePairBy(
    path: ConfigurePath,
    op: ConfigureOp.PairBy[_],
  ): Either[ConfigureError, Differ[T]] = {
    Left(ConfigureError.InvalidConfigureOp(path, op, "AlwaysIgnoreDiffer"))
  }
}

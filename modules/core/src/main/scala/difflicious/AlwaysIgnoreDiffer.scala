package difflicious

import difflicious.DiffResult.ValueResult
import difflicious.utils.TypeName

/** A Differ that always return an Ignored result. Useful when you can't really diff a type
  */
final class AlwaysIgnoreDiffer[T](typeName: TypeName[T]) extends Differ[T] {
  override type R = ValueResult

  override val canUseEquals: Boolean = false

  override def diff(inputs: DiffInput[T]): ValueResult =
    ValueResult.Both(typeName, "[ALWAYS IGNORED]", "[ALWAYS IGNORED]", isSame = true, isIgnored = true)

  override protected def configureIgnored(newIgnored: Boolean): Differ[T] = this

  override protected def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[T]] = Left(ConfigureError.PathTooLong(nextPath))

  override protected def configurePairBy(
    path: ConfigurePath,
    op: ConfigureOp.PairBy[?],
  ): Either[ConfigureError, Differ[T]] = {
    Left(ConfigureError.InvalidConfigureOp(path, op, "AlwaysIgnoreDiffer"))
  }
}

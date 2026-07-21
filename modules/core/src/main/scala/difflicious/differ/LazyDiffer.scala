package difflicious.differ

import difflicious.*

/** A wrapper over another Differ that lazily initializes it when needed. Used to break self-referencing cycles such as
  * recursive data structures
  */
final class LazyDiffer[T](mkDiffer: => Differ[T]) extends Differ[T] {
  override type R = DiffResult

  @volatile private var initializingUnderlying: Boolean = false
  private lazy val underlying: Differ[T] = {
    initializingUnderlying = true
    try mkDiffer
    finally initializingUnderlying = false
  }
  private var evaluatingCanUseEquals: Boolean = false

  override def canUseEquals: Boolean = {
    if (initializingUnderlying || evaluatingCanUseEquals) false
    else {
      evaluatingCanUseEquals = true
      try underlying.canUseEquals
      finally evaluatingCanUseEquals = false
    }
  }

  override def diff(inputs: DiffInput[T]): DiffResult =
    underlying.diff(inputs)

  override protected def configureIgnored(newIgnored: Boolean): Differ[T] =
    LazyDiffer.configureOrThrow(
      underlying.configureRaw(ConfigurePath.current, ConfigureOp.SetIgnored(newIgnored)),
    )

  override protected def configurePath(
    step: String,
    nextPath: ConfigurePath,
    op: ConfigureOp,
  ): Either[ConfigureError, Differ[T]] =
    underlying.configureRaw(
      ConfigurePath(nextPath.resolvedSteps.dropRight(1), step :: nextPath.unresolvedSteps),
      op,
    )

  override protected def configurePairBy(
    path: ConfigurePath,
    op: ConfigureOp.PairBy[?],
  ): Either[ConfigureError, Differ[T]] =
    underlying.configureRaw(path, op)
}

object LazyDiffer {
  private def configureOrThrow[T](configured: Either[ConfigureError, Differ[T]]): Differ[T] =
    configured match {
      case Right(differ) => differ
      case Left(error) => throw error
    }
}

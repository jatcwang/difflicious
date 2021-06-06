package difflicious.differ

import difflicious.{Differ, DiffResult, DiffInput}

trait ValueDiffer[T] extends Differ[T] {
  final override type R = DiffResult.ValueResult

  override def diff(inputs: DiffInput[T]): R
}

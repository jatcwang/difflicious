package difflicious.internal

import difflicious.DiffResult

private[difflicious] object SumCountsSyntax {
  implicit class DiffResultIterableOps(iterable: Iterable[DiffResult]) {
    def differenceCount: Int = iterable.foldLeft(0) { (acc, next) => acc + next.differenceCount }
    def ignoredCount: Int = iterable.foldLeft(0) { (acc, next) => acc + next.ignoredCount }
  }
}

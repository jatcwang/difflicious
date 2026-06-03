package difflicious.testutils

object SnapshotPaths:
  def apply(suiteName: String, snapshotName: String): String =
    s"snapshots/scala-3/$suiteName/$snapshotName"

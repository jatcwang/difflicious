package difflicious.sbt

import sbt.*
import sbt.Keys.*
import sbt.internal.server.ClientJob

private[sbt] object ClientJobCompat {
  def configurationSettings(configuration: Configuration): Seq[Def.Setting[_]] =
    inConfig(configuration)(ClientJob.configSettings)

  def run(
    project: ProjectRef,
    configuration: Configuration,
    arguments: String,
  ): Def.Initialize[Task[Unit]] =
    (project / configuration / clientJobRunInfo).toTask(s" $arguments").map(_ => ())
}

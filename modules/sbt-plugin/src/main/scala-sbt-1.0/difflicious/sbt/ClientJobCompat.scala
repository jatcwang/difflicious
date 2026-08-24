package difflicious.sbt

import sbt.*
import sbt.Keys.*
import sbt.internal.server.ClientJob

private[sbt] object ClientJobCompat {
  def configurationSettings(configuration: Configuration): Seq[Def.Setting[_]] =
    inConfig(configuration)(
      ClientJob.configSettings ++
        Seq(
          Keys.run := Defaults
            .runTask(fullClasspath, Def.task(Some("difflicious.cli.Main")), Keys.run / runner)
            .evaluated,
        ) ++
        inTask(Keys.run)(Defaults.runnerSettings),
    )

  def run(
    project: ProjectRef,
    configuration: Configuration,
    arguments: String,
  ): Def.Initialize[Task[Unit]] = Def.taskDyn {
    val runArguments = s" $arguments"
    val isNetworkClient =
      state.value.currentCommand.flatMap(_.source).exists(_.channelName.startsWith("network"))

    if (isNetworkClient)
      (project / configuration / clientJobRunInfo).toTask(runArguments).map(_ => ())
    else {
      val scope = Scope(
        Select(project),
        Select(ConfigKey(configuration.name)),
        Zero,
        Zero,
      )
      Scoped.scopedInput(scope, Keys.run.key).toTask(runArguments).map(_ => ())
    }
  }
}

package difflicious.sbt

import sbt.*

private[sbt] object ClientJobCompat {
  def configurationSettings(configuration: Configuration): Seq[Def.Setting[?]] =
    inConfig(configuration)(_root_.sbt.DiffliciousClientJobCompat.settings(configuration))

  def run(
    project: ProjectRef,
    configuration: Configuration,
    arguments: String,
  ): Def.Initialize[Task[Unit]] = {
    val scope = Scope(
      ScopeAxis.Select(project),
      ScopeAxis.Select(ConfigKey(configuration.name)),
      ScopeAxis.Zero,
      ScopeAxis.Zero,
    )
    Scoped.scopedInput(scope, Keys.run.key).toTask(s" $arguments").map(_ => ())
  }
}

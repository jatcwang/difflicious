package sbt

import sbt.internal.RunUtil

object DiffliciousClientJobCompat {
  def settings(configuration: Configuration): Seq[Def.Setting[?]] =
    RunUtil.configTasks(ScopeAxis.Select(ConfigKey(configuration.name))) ++ Seq(
      Keys.run / Keys.runner := Def.uncached {
        new ForkRun((Keys.run / Keys.forkOptions).value)
      },
    )
}

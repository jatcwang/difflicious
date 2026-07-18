package sbt

import sbt.internal.RunUtil

object DiffliciousClientJobCompat {
  def settings(configuration: Configuration): Seq[Def.Setting[?]] =
    RunUtil.configTasks(ScopeAxis.Select(ConfigKey(configuration.name)))
}

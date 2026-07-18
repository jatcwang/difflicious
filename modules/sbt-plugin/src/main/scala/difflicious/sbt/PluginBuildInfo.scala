package difflicious.sbt

import java.util.Properties

private[sbt] object PluginBuildInfo {
  def version: String =
    properties.getProperty("version", "0.1.0-SNAPSHOT")

  private lazy val properties: Properties = {
    val result = new Properties()
    val resource = getClass.getClassLoader.getResourceAsStream("sbt-difflicious.properties")
    if (resource != null) {
      try result.load(resource)
      finally resource.close()
    }
    result
  }
}

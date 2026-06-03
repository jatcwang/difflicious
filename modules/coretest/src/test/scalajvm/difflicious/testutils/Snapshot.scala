package difflicious.testutils

object Snapshot {

  def read(path: String): String = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse {
      throw new IllegalArgumentException(s"Snapshot resource does not exist: $path")
    }
    val source = scala.io.Source.fromInputStream(stream, "UTF-8")
    try stripFinalNewline(source.mkString)
    finally source.close()
  }

  private def stripFinalNewline(content: String): String =
    if (content.endsWith("\r\n")) content.dropRight(2)
    else if (content.endsWith("\n")) content.dropRight(1)
    else content
}

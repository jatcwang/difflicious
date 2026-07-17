package difflicious.cli

final case class DiffPath(segments: Vector[DiffPath.Segment]) {
  def /(segment: DiffPath.Segment): DiffPath =
    copy(segments = segments :+ segment)

  def render: String =
    if (segments.isEmpty) "$"
    else segments.foldLeft("$")((accum, segment) => accum + segment.render)
}

object DiffPath {
  val root: DiffPath = DiffPath(Vector.empty)

  sealed trait Segment {
    def render: String
  }

  final case class Field(name: String) extends Segment {
    override def render: String =
      if (isSimpleIdentifier(name)) s".$name"
      else s"[${quote(name)}]"
  }

  final case class Index(value: Int) extends Segment {
    override def render: String = s"[$value]"
  }

  private def isSimpleIdentifier(value: String): Boolean =
    value.nonEmpty &&
      (value.charAt(0).isLetter || value.charAt(0) == '_') &&
      value.forall(ch => ch.isLetterOrDigit || ch == '_')

  private def quote(value: String): String = {
    val builder = new StringBuilder(value.length + 2)
    builder.append('"')
    value.foreach {
      case '"' => builder.append("\\\"")
      case '\\' => builder.append("\\\\")
      case '\b' => builder.append("\\b")
      case '\f' => builder.append("\\f")
      case '\n' => builder.append("\\n")
      case '\r' => builder.append("\\r")
      case '\t' => builder.append("\\t")
      case ch if Character.isISOControl(ch) =>
        val hex = ch.toInt.toHexString
        builder.append("\\u")
        builder.append("0" * (4 - hex.length))
        builder.append(hex)
      case ch => builder.append(ch)
    }
    builder.append('"')
    builder.result()
  }
}

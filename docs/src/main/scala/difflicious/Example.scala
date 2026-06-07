package difflicious

object Example {
  case class Person(name: String, age: Int)

  object Person {
    implicit val differ: Differ[Person] = Differ.derived[Person]
  }

  def diffHtml(diffResult: DiffResult): String =
    "<pre className=\"diff-render\" dangerouslySetInnerHTML={{ __html: " +
      jsStringLiteral(renderConsoleOutputHtml(diffResult)) +
      " }} />"

  def renderConsoleOutputHtml(diffResult: DiffResult): String =
    ansiToHtml(DiffResultPrinter.consoleOutput(diffResult, 0).render)

  private def ansiToHtml(value: String): String = {
    val builder = new StringBuilder(value.length)
    var index = 0
    var openSpan = false

    def closeSpan(): Unit =
      if (openSpan) {
        builder.append("</span>")
        openSpan = false
      }

    def openSpanWithClass(className: String): Unit = {
      closeSpan()
      builder.append("<span class=\"")
      builder.append(className)
      builder.append("\">")
      openSpan = true
    }

    def handleAnsiCode(code: String): Unit = {
      val parts = code.split(";").toSet
      if (parts.contains("0") || parts.contains("39")) closeSpan()
      if (parts.contains("31")) openSpanWithClass("diff-red")
      else if (parts.contains("32")) openSpanWithClass("diff-green")
      else if (parts.contains("90")) openSpanWithClass("diff-gray")
    }

    while (index < value.length) {
      if (value.charAt(index) == '\u001b' && index + 1 < value.length && value.charAt(index + 1) == '[') {
        val codeStart = index + 2
        var codeEnd = codeStart
        while (codeEnd < value.length && value.charAt(codeEnd) != 'm') {
          codeEnd += 1
        }

        if (codeEnd < value.length) {
          handleAnsiCode(value.substring(codeStart, codeEnd))
          index = codeEnd + 1
        } else {
          appendEscaped(builder, value.charAt(index))
          index += 1
        }
      } else {
        appendEscaped(builder, value.charAt(index))
        index += 1
      }
    }

    closeSpan()
    builder.result()
  }

  private def appendEscaped(builder: StringBuilder, char: Char): Unit =
    char match {
      case '&' => builder.append("&amp;")
      case '<' => builder.append("&lt;")
      case '>' => builder.append("&gt;")
      case '"' => builder.append("&quot;")
      case '\'' => builder.append("&#39;")
      case '{' => builder.append("&#123;")
      case '}' => builder.append("&#125;")
      case _ => builder.append(char)
    }

  private def jsStringLiteral(value: String): String = {
    val builder = new StringBuilder(value.length + 2)
    builder.append('"')

    value.foreach {
      case '\\' => builder.append("\\\\")
      case '"' => builder.append("\\\"")
      case '\n' => builder.append("\\n")
      case '\r' => builder.append("\\r")
      case '\t' => builder.append("\\t")
      case char if char < ' ' =>
        val hex = Integer.toHexString(char.toInt)
        builder.append("\\u")
        (hex.length until 4).foreach(_ => builder.append('0'))
        builder.append(hex)
      case char => builder.append(char)
    }

    builder.append('"')
    builder.result()
  }
}

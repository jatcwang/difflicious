package difflicious.testutils

object TreeRendering {

  def simplify(rendered: String): String =
    simplifyMaterializedTypeNames(standardLibrarySimplifications.foldLeft(rendered) { case (tree, (from, to)) =>
      tree.replace(from, to)
    })

  // TODO: Hm this aint great - find a way to get rid of this at some point
  private def simplifyMaterializedTypeNames(rendered: String): String = {
    val prefix = "difflicious.utils.TypeName.apply["
    val output = new StringBuilder(rendered.length)
    var index = 0

    while (index < rendered.length) {
      val start = rendered.indexOf(prefix, index)
      if (start == -1) {
        output.append(rendered.substring(index))
        index = rendered.length
      } else {
        output.append(rendered.substring(index, start))

        val typeStart = start + prefix.length
        val typeEnd = findMatching(rendered, typeStart - 1, '[', ']')
        val argsStart = typeEnd + 1

        if (typeEnd == -1 || argsStart >= rendered.length || rendered.charAt(argsStart) != '(') {
          output.append(prefix)
          index = typeStart
        } else {
          val argsEnd = findMatching(rendered, argsStart, '(', ')')
          if (argsEnd == -1) {
            output.append(rendered.substring(start))
            index = rendered.length
          } else {
            val call = rendered.substring(start, argsEnd + 1)
            val _ =
              if (call.contains("izumi.reflect.macrortti.")) {
                output.append(prefix)
                output.append(rendered.substring(typeStart, typeEnd))
                output.append("](/*irrelevant*/)")
              } else {
                output.append(call)
              }
            index = argsEnd + 1
          }
        }
      }
    }

    output.toString
  }

  private def findMatching(text: String, openIndex: Int, open: Char, close: Char): Int = {
    var depth = 0
    var index = openIndex
    var inString = false
    var inTripleString = false
    var escaped = false

    while (index < text.length) {
      val char = text.charAt(index)
      if (inTripleString) {
        if (startsWith(text, index, "\"\"\"")) {
          inTripleString = false
          index += 2
        }
      } else if (inString) {
        if (escaped) escaped = false
        else if (char == '\\') escaped = true
        else if (char == '"') inString = false
      } else if (startsWith(text, index, "\"\"\"")) {
        inTripleString = true
        index += 2
      } else if (char == '"') {
        inString = true
      } else if (char == open) {
        depth += 1
      } else if (char == close) {
        depth -= 1
        if (depth == 0) return index
      }
      index += 1
    }

    -1
  }

  private def startsWith(text: String, index: Int, prefix: String): Boolean =
    index + prefix.length <= text.length && text.regionMatches(index, prefix, 0, prefix.length)

  private val standardLibrarySimplifications: List[(String, String)] =
    List(
      "scala.collection.immutable." -> "",
      "scala.Predef." -> "",
      "scala.`package`." -> "",
      "scala.util." -> "",
      "java.lang." -> "",
      "scala.Any" -> "Any",
      "scala.Nothing" -> "Nothing",
      "scala.Option" -> "Option",
      "scala.Some" -> "Some",
      "scala.None" -> "None",
      "scala.Tuple2" -> "Tuple2",
      "scala.Function1" -> "Function1",
      "scala.Product" -> "Product",
      "scala.Nil" -> "Nil",
    )
}

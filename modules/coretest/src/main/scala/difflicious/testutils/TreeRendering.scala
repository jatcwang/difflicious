package difflicious.testutils

object TreeRendering {

  def simplify(rendered: String): String =
    stripTrailingWhitespace(
      standardLibrarySimplifications.foldLeft(rendered) { case (tree, (from, to)) =>
        tree.replace(from, to)
      },
    )

  private def stripTrailingWhitespace(rendered: String): String =
    rendered.linesIterator.map(_.stripTrailing).mkString("\n")

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

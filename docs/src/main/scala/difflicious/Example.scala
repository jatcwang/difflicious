package difflicious

object Example {
  case class Person(name: String, age: Int)

  object Person {
    implicit val differ: Differ[Person] = Differ.derived[Person]
  }

  def printHtml(diffResult: DiffResult) = {

    val RED = "\u001b[31m"
    val GREEN = "\u001b[32m"
    val GRAY = "\u001b[90m"
    val RESET = "\u001b[39m"
    //      val xx = difflicious.DiffResultPrinter
    //        .consoleOutput(diffResult, 0)
    //        .render
    difflicious.DiffResultPrinter
      .consoleOutput(diffResult, 0)
      .render
      .replace(RED, """<span className="diff-red">""")
      .replace(GREEN, """<span className="diff-green">""")
      .replace(GRAY, """<span className="diff-gray">""")
      .replace(RESET, "</span>")
  }
}

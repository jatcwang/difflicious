package difflicious

object Example {
  case class Person(name: String, age: Int)

  object Person {
    implicit val differ: Differ[Person] = Differ.derive[Person]
  }

  def printHtml(diffResult: DiffResult) = {

    val RED = "\u001b[31m"
    val GREEN = "\u001b[32m"
    val RESET = "\u001b[39m"
    //      val xx = difflicious.DiffResultPrinter
    //        .consoleOutput(diffResult, 0)
    //        .render
    difflicious.DiffResultPrinter
      .consoleOutput(diffResult, 0)
      .render
      .replace(RED, """<span style="color: red;">""")
      .replace(GREEN, """<span style="color: green;">""")
      .replace(RESET, "</span>")
  }

  def main(args: Array[String]): Unit = {
    printHtml(
      Differ[List[Person]]
        .diff(List(Person("Alice", 30), Person("Bob", 25)), List(Person("Bob", 25), Person("Alice", 30))),
    )
  }
}

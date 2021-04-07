package difflicious
import izumi.reflect.Tag
import izumi.reflect.macrortti.LTag

object FixmeMain {
  def main(args: Array[String]): Unit = {
    val t = LTag[Either[Int, String]]
    val y = Tag[Either[Int, String]]

    println(t.tag)
  }
}

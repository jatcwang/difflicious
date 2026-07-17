package difflicious.reporter

trait UlidGenerator {
  def generate(): String
}

object UlidGenerator {
  val Default: UlidGenerator = new UlidGenerator {
    override def generate(): String = Ulid.generate()
  }
}

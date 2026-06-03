package difflicious

import java.util.concurrent.TimeUnit

import difflicious.differ.ValueDiffer
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class DiffBench {

  val big = Big(
    12,
    "asdfa",
    Map(Key("a", 1) -> Dog("fido", 3.5)),
    Vector(Dog("spot", 7.0)),
    Set(Dog("rex", 2.0)),
  )

  val bigClone = Big(
    12,
    "asdfa",
    Map(Key("a", 1) -> Dog("fido", 3.5)),
    Vector(Dog("spot", 7.0)),
    Set(Dog("rex", 2.0)),
  )

  @Benchmark
  def usingDiff(bh: Blackhole): Unit = {
    bh.consume(Big.diff.diff(big, bigClone))
  }

  @Benchmark
  def usingDiffObtainedOnly(bh: Blackhole): Unit = {
    bh.consume(Big.diff.diff(DiffInput.ObtainedOnly(big)))
  }

  @Benchmark
  def usingDiffExpectedOnly(bh: Blackhole): Unit = {
    bh.consume(Big.diff.diff(DiffInput.ExpectedOnly(bigClone)))
  }

  @Benchmark
  def usingEquals(bh: Blackhole): Unit = {
    bh.consume(big == bigClone)
  }
}

final case class Big(
  i: Int,
  s: String,
  map: Map[Key, Dog],
  list: Vector[Dog],
  set: Set[Dog],
)

object Big {
  implicit val diff: Differ[Big] = Differ.derived[Big]
}

final case class Dog(
  name: String,
  age: Double,
)

object Dog {
  implicit val diff: Differ[Dog] = Differ.derived[Dog]
}

final case class Key(
  name: String,
  x: Int,
)

object Key {
  implicit val diff: ValueDiffer[Key] = Differ.useEquals[Key](_.toString)
}

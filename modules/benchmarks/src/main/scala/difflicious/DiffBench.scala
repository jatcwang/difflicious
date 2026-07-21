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

  val bigDifferent = Big(
    12,
    "asdfa",
    Map(Key("a", 1) -> Dog("fido", 3.5)),
    Vector(Dog("spot", 7.0)),
    Set(Dog("rex", 3.0)),
  )

  @Benchmark
  def usingDiffer(bh: Blackhole): Unit = {
    bh.consume(Big.differ.diff(big, bigClone))
  }

  @Benchmark
  def usingEqualsOrDiff(bh: Blackhole): Unit = {
    bh.consume(Big.differ.equalsOrDiff(big, bigClone))
  }

  @Benchmark
  def usingDifferDifferent(bh: Blackhole): Unit = {
    bh.consume(Big.differ.diff(big, bigDifferent))
  }

  @Benchmark
  def usingEqualsOrDiffDifferent(bh: Blackhole): Unit = {
    bh.consume(Big.differ.equalsOrDiff(big, bigDifferent))
  }

  @Benchmark
  def usingDifferObtainedOnly(bh: Blackhole): Unit = {
    bh.consume(Big.differ.diff(DiffInput.ObtainedOnly(big)))
  }

  @Benchmark
  def usingDifferExpectedOnly(bh: Blackhole): Unit = {
    bh.consume(Big.differ.diff(DiffInput.ExpectedOnly(bigClone)))
  }

  @Benchmark
  def usingPlainEquals(bh: Blackhole): Unit = {
    bh.consume(big == bigClone)
  }

  @Benchmark
  def usingPlainEqualsDifferent(bh: Blackhole): Unit = {
    bh.consume(big == bigDifferent)
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
  implicit val differ: Differ[Big] = Differ.derived[Big]
}

final case class Dog(
  name: String,
  age: Double,
)

object Dog {
  implicit val differ: Differ[Dog] = Differ.derived[Dog]
}

final case class Key(
  name: String,
  x: Int,
)

object Key {
  implicit val differ: ValueDiffer[Key] = Differ.useEquals[Key](_.toString)
}

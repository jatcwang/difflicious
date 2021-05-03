package difflicious

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class DiffBench {

  val big = Big(
    12,
    "asdfa",
  )

  val bigClone = Big(
    12,
    "asdfa",
  )

  @Benchmark
  def usingDiff(bh: Blackhole): Unit = {
    bh.consume(checkDiff(big, bigClone))
  }

  @Benchmark
  def usingEquals(bh: Blackhole): Unit = {
    bh.consume(big == bigClone)
  }
}

final case class Big(
  i: Int,
  s: String,
  //    map: Map[Key, Dog],
  //    list: Vector[Dog],
  //    set: Set[Dog],
)

object Big {
  implicit val diff: Differ[Big] = Differ.derive[Big]
}

final case class Dog(
  name: String,
  age: Double,
)

final case class Key(
  name: String,
  x: Int,
)

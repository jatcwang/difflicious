package difflicious

import difflicious.testutils.testtypes.Big

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

package io.macavity.benchmark

import cats.data.{ StateT => CStateT }
import cats.std.future._
import cats.std.list._
import cats.syntax.traverse._
import io.macavity.data.{ StateT => MStateT }
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Compare the performance of encoding operations.
 *
 * The following command will run the benchmarks with reasonable settings:
 *
 * > sbt "benchmark/jmh:run -i 10 -wi 10 -f 2 -t 1 io.macavity.benchmark.StateTBenchmark"
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class StateTBenchmark {
  val values: List[Int] = (0 to 10000).toList

  def cStateT(value: Int): CStateT[Future, Int, Int] = CStateT(i => Future((i + 1, value + i)))
  def mStateT(value: Int): MStateT[Future, Int, Int] = MStateT(i => Future((i + 1, value + i)))

  @Benchmark
  def traverseStateC: CStateT[Future, Int, List[Int]] = values.traverseU(cStateT)

  @Benchmark
  def traverseStateM: MStateT[Future, Int, List[Int]] = values.traverseU(mStateT)
}

package io.macavity.benchmark

import cats.Eval
import cats.data.{ Kleisli => CKleisli }
import cats.std.list._
import cats.syntax.traverse._
import io.macavity.data.{ Kleisli => MKleisli }
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

/**
 * Compare the performance of encoding operations.
 *
 * The following command will run the benchmarks with reasonable settings:
 *
 * > sbt "benchmark/jmh:run -i 10 -wi 10 -f 2 -t 1 io.macavity.benchmark.KleisliBenchmark"
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class KleisliBenchmark {
  val values: List[Int] = (0 to 1000).toList

  def cKleisli(value: Int): CKleisli[Eval, Int, Int] = CKleisli[Eval, Int, Int](i => Eval.now(value + i))
  def mKleisli(value: Int): MKleisli[Eval, Int, Int] = new MKleisli[Eval, Int, Int] {
    final def run(i: Int): Eval[Int] = Eval.now(value + i)
  }

  @Benchmark
  def traverseKleisliC: List[Int] = values.traverseU(cKleisli).run(0).value

  @Benchmark
  def traverseKleisliM: List[Int] = values.traverseU(mKleisli).run(0).value
}

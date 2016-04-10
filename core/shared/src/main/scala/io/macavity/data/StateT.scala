package io.macavity.data

import algebra.Monoid
import cats.{ Applicative, FlatMap, Functor, Monad, MonadState, TransLift }

abstract class StateT[F[_], S, A] extends Serializable { self =>
  def run(initial: S): F[(S, A)]

  final def flatMap[B](f: A => StateT[F, S, B])(implicit F: FlatMap[F]): StateT[F, S, B] =
    new StateT[F, S, B] {
      final def run(initial: S): F[(S, B)] = F.flatMap(self.run(initial)) {
        case (s, a) => f(a).run(s)
      }
    }

  final def map[B](f: A => B)(implicit F: FlatMap[F]): StateT[F, S, B] = new StateT[F, S, B] {
    final def run(initial: S): F[(S, B)] = F.map(self.run(initial)) {
      case (s, a) => (s, f(a))
    }
  }

  /**
   * Run with the provided initial state value and return the final state
   * (discarding the final value).
   */
  final def runS(s: S)(implicit F: Functor[F]): F[S] = F.map(run(s))(_._1)

  /**
   * Run with the provided initial state value and return the final value
   * (discarding the final state).
   */
  final def runA(s: S)(implicit F: Functor[F]): F[A] = F.map(run(s))(_._2)

  /**
   * Run with `S`'s empty monoid value as the initial state.
   */
  final def runEmpty(implicit S: Monoid[S], F: Functor[F]): F[(S, A)] = run(S.empty)

  /**
   * Run with `S`'s empty monoid value as the initial state and return the final
   * state (discarding the final value).
   */
  final def runEmptyS(implicit S: Monoid[S], F: Functor[F]): F[S] = runS(S.empty)

  /**
   * Run with `S`'s empty monoid value as the initial state and return the final
   * value (discarding the final state).
   */
  final def runEmptyA(implicit S: Monoid[S], F: Functor[F]): F[A] = runA(S.empty)

  /**
   * Like [[map]], but also allows the state (`S`) value to be modified.
   */
  final def transform[B](f: (S, A) => (S, B))(implicit F: Functor[F]): StateT[F, S, B] = new StateT[F, S, B] {
    final def run(initial: S): F[(S, B)] = F.map(self.run(initial)) {
      case (s, a) => f(s, a)
    }
  }

  /**
   * Like [[transform]], but allows the context to change from `F` to `G`.
   */
  final def transformF[G[_], B](f: F[(S, A)] => G[(S, B)]): StateT[G, S, B] = new StateT[G, S, B] {
    final def run(initial: S): G[(S, B)] = f(self.run(initial))
  }

  /**
   * Transform the state used.
   *
   * This is useful when you are working with many focused `StateT`s and want to pass in a
   * global state containing the various states needed for each individual `StateT`.
   */
  final def transformS[R](f: R => S, g: (R, S) => R)(implicit F: Functor[F]): StateT[F, R, A] = new StateT[F, R, A] {
    final def run(initial: R): F[(R, A)] = F.map(self.run(f(initial))) {
      case (s, a) => (g(initial, s), a)
    }
  }

  /**
   * Modify the state (`S`) component.
   */
  final def modify(f: S => S)(implicit F: Functor[F]): StateT[F, S, A] = new StateT[F, S, A] {
    final def run(initial: S): F[(S, A)] = F.map(self.run(initial)) {
      case (s, a) => (f(s), a)
    }
  }

  /**
   * Inspect a value from the input state, without modifying the state.
   */
  final def inspect[B](f: S => B)(implicit F: Functor[F]): StateT[F, S, B] = new StateT[F, S, B] {
    final def run(initial: S): F[(S, B)] = F.map(self.run(initial))(p => (p._1, f(p._1)))
  }

  /**
    * Get the input state, without modifying the state.
    */
  final def get(implicit F: Functor[F]): StateT[F, S, S] = new StateT[F, S, S] {
    final def run(initial: S): F[(S, S)] = F.map(self.run(initial))(p => (p._1, p._1))
  }
}

final object StateT {
  def apply[F[_], S, A](f: S => F[(S, A)]): StateT[F, S, A] = new StateT[F, S, A] {
    final def run(initial: S): F[(S, A)] = f(initial)
  }

  def pure[F[_], S, A](a: A)(implicit F: Applicative[F]): StateT[F, S, A] = new StateT[F, S, A] {
    final def run(initial: S): F[(S, A)] = F.pure((initial, a))
  }

  implicit def stateTMonadState[F[_], S](implicit
    F: Monad[F]
  ): MonadState[({ type L[x] = StateT[F, S, x] })#L, S] =
    new MonadState[({ type L[x] = StateT[F, S, x] })#L, S] {
      final def pure[A](a: A): StateT[F, S, A] = StateT.pure(a)
      final override def map[A, B](fa: StateT[F, S, A])(f: A => B): StateT[F, S, B] = fa.map(f)
      final def flatMap[A, B](fa: StateT[F, S, A])(f: A => StateT[F, S, B]): StateT[F, S, B] = fa.flatMap(f)
      final val get: StateT[F, S, S] = new StateT[F, S, S] {
        final def run(initial: S): F[(S, S)] = F.pure((initial, initial))
      }
      final def set(s: S): StateT[F, S, Unit] = new StateT[F, S, Unit] {
        final def run(initial: S): F[(S, Unit)] = F.pure((s, ()))
      }
    }

  implicit def stateTLift[S]: TransLift.Aux[({ type L[f[_], x] = StateT[f, S, x] })#L, Applicative] =
    new TransLift[({ type L[f[_], x] = StateT[f, S, x] })#L] {
      final type TC[M[_]] = Applicative[M]

      final def liftT[M[_], A](ma: M[A])(implicit M: Applicative[M]): StateT[M, S, A] = new StateT[M, S, A] {
        final def run(initial: S): M[(S, A)] = M.map(ma)(a => (initial, a))
      }
    }
}

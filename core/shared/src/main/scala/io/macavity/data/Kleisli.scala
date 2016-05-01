package io.macavity.data

import cats._
import cats.arrow.{ Arrow, Choice, Split }
import cats.data.Xor
import cats.functor.{ Contravariant, Strong }

/**
 * Represents a function `A => F[B]`.
 */
abstract class Kleisli[F[_], A, B] { self =>
  def run(a: A): F[B]

  final def ap[C](f: Kleisli[F, A, B => C])(implicit F: Apply[F]): Kleisli[F, A, C] =
    new Kleisli[F, A, C] {
      final def run(a: A): F[C] = F.ap(f.run(a))(self.run(a))
    }

  final def dimap[C, D](f: C => A)(g: B => D)(implicit F: Functor[F]): Kleisli[F, C, D] =
    new Kleisli[F, C, D] {
      final def run(a: C): F[D] = F.map(self.run(f(a)))(g)
    }

  final def map[C](f: B => C)(implicit F: Functor[F]): Kleisli[F, A, C] =
    new Kleisli[F, A, C] {
      final def run(a: A): F[C] = F.map(self.run(a))(f)
    }

  final def mapF[N[_], C](f: F[B] => N[C]): Kleisli[N, A, C] =
    new Kleisli[N, A, C] {
      final def run(a: A): N[C] = f(self.run(a))
    }

  final def flatMap[C](f: B => Kleisli[F, A, C])(implicit F: FlatMap[F]): Kleisli[F, A, C] =
    new Kleisli[F, A, C] {
      final def run(a: A): F[C] = F.flatMap[B, C](self.run(a))((b: B) => f(b).run(a))
    }

  final def flatMapF[C](f: B => F[C])(implicit F: FlatMap[F]): Kleisli[F, A, C] =
    new Kleisli[F, A, C] {
      final def run(a: A): F[C] = F.flatMap(self.run(a))(f)
    }

  final def andThen[C](f: B => F[C])(implicit F: FlatMap[F]): Kleisli[F, A, C] =
    new Kleisli[F, A, C] {
      final def run(a: A): F[C] = F.flatMap(self.run(a))(f)
    }

  final def andThen[C](k: Kleisli[F, B, C])(implicit F: FlatMap[F]): Kleisli[F, A, C] =
    andThen(k.run(_))

  final def compose[Z](f: Z => F[A])(implicit F: FlatMap[F]): Kleisli[F, Z, B] =
    new Kleisli[F, Z, B] {
      final def run(a: Z) = F.flatMap(f(a))(self.run)
    }

  final def compose[Z](k: Kleisli[F, Z, A])(implicit F: FlatMap[F]): Kleisli[F, Z, B] =
    compose(k.run(_))

  final def traverse[G[_]](f: G[A])(implicit F: Applicative[F], G: Traverse[G]): F[G[B]] =
    G.traverse(f)(run)

  final def lift[G[_]](implicit G: Applicative[G]): Kleisli[({ type L[x] = G[F[x]] })#L, A, B] =
    new Kleisli[({ type L[x] = G[F[x]] })#L, A, B] {
      final def run(a: A): G[F[B]] = G.pure(self.run(a))
    }

  final def local[AA](f: AA => A): Kleisli[F, AA, B] = new Kleisli[F, AA, B] {
    final def run(a: AA): F[B] = self.run(f(a))
  }

  final def transform[G[_]](f: F ~> G): Kleisli[G, A, B] = new Kleisli[G, A, B] {
    final def run(a: A): G[B] = f(self.run(a))
  }

  final def lower(implicit F: Applicative[F]): Kleisli[F, A, F[B]] = new Kleisli[F, A, F[B]] {
    final def run(a: A): F[F[B]] = F.pure(self.run(a))
  }

  final def first[C](implicit F: Functor[F]): Kleisli[F, (A, C), (B, C)] = new Kleisli[F, (A, C), (B, C)] {
    final def run(a: (A, C)): F[(B, C)] = F.fproduct(self.run(a._1))(_ => a._2)
  }

  final def second[C](implicit F: Functor[F]): Kleisli[F, (C, A), (C, B)] = new Kleisli[F, (C, A), (C, B)] {
    final def run(a: (C, A)): F[(C, B)] = F.map(self.run(a._2))(a._1 -> _)
  }

  final def zip[C](fc: Kleisli[F, A, C])(implicit F: Cartesian[F]): Kleisli[F, A, (B, C)] = new Kleisli[F, A, (B, C)] {
    final def run(a: A): F[(B, C)] = F.product(self.run(a), fc.run(a))
  }

  final def apply(a: A): F[B] = run(a)
}

final object Kleisli extends KleisliInstances {
  def apply[F[_], A, B](f: A => F[B]): Kleisli[F, A, B] = new Kleisli[F, A, B] {
    final def run(a: A): F[B] = f(a)
  }

  def const[F[_], A, B](fb: F[B]): Kleisli[F, A, B] = new Kleisli[F, A, B] {
    final def run(a: A): F[B] = fb
  }

  def pure[F[_], A, B](x: B)(implicit F: Applicative[F]): Kleisli[F, A, B] = new Kleisli[F, A, B] {
    final def run(a: A): F[B] = F.pure(x)
  }

  def ask[F[_], A](implicit F: Applicative[F]): Kleisli[F, A, A] = new Kleisli[F, A, A] {
    final def run(a: A): F[A] = F.pure(a)
  }

  def local[M[_], A, R](f: R => R)(fa: Kleisli[M, R, A]): Kleisli[M, R, A] = new Kleisli[M, R, A] {
    final def run(a: R): M[A] = fa.run(f(a))
  }
}

private[data] sealed class KleisliInstances extends KleisliInstances0 {
  implicit final def kleisliMonoid[F[_], A, B](implicit M: Monoid[F[B]]): Monoid[Kleisli[F, A, B]] =
    new KleisliMonoid[F, A, B] { final def FB: Monoid[F[B]] = M }

  implicit final def kleisliMonoidK[F[_]](implicit M: Monad[F]): MonoidK[({ type L[x] = Kleisli[F, x, x] })#L] =
    new KleisliMonoidK[F] { final def F: Monad[F] = M }

  implicit final val kleisliIdMonoidK: MonoidK[({ type L[x] = Kleisli[Id, x, x] })#L] = kleisliMonoidK[Id]

  implicit final def kleisliArrow[F[_]](implicit ev: Monad[F]): Arrow[({ type L[x, y] = Kleisli[F, x, y] })#L] =
    new KleisliArrow[F] { final def F: Monad[F] = ev }

  implicit final val kleisliIdArrow: Arrow[({ type L[x, y] = Kleisli[Id, x, y] })#L] = kleisliArrow[Id]

  implicit def kleisliChoice[F[_]](implicit ev: Monad[F]): Choice[({ type L[x, y] = Kleisli[F, x, y] })#L] =
    new Choice[({ type L[x, y] = Kleisli[F, x, y] })#L] {
      final def id[A]: Kleisli[F, A, A] = Kleisli.ask[F, A]

      final def choice[A, B, C](f: Kleisli[F, A, C], g: Kleisli[F, B, C]): Kleisli[F, Xor[A, B], C] =
        new Kleisli[F, Xor[A, B], C] {
          final def run(a: Xor[A, B]): F[C] = a match {
            case Xor.Left(l) => f.run(l)
            case Xor.Right(r) => g.run(r)
          }
        }

      final def compose[A, B, C](f: Kleisli[F, B, C], g: Kleisli[F, A, B]): Kleisli[F, A, C] = f.compose(g)
    }

  implicit final val kleisliIdChoice: Choice[({ type L[x, y] = Kleisli[Id, x, y] })#L] = kleisliChoice[Id]

  implicit final def kleisliIdMonadReader[A]: MonadReader[({ type L[x] = Kleisli[Id, A, x] })#L, A] =
    kleisliMonadReader[Id, A]

  implicit final def kleisliContravariant[F[_], C]: Contravariant[({ type L[x] = Kleisli[F, x, C] })#L] =
    new Contravariant[({ type L[x] = Kleisli[F, x, C] })#L] {
      override final def contramap[A, B](fa: Kleisli[F, A, C])(f: (B) => A): Kleisli[F, B, C] = fa.local(f)
    }

  implicit def kleisliTransLift[A]: TransLift.AuxId[({ type L[f[_], x] = Kleisli[f, A, x] })#L] =
    new TransLift[({ type L[f[_], x] = Kleisli[f, A, x] })#L] {
      type TC[M[_]] = Trivial

      final def liftT[M[_], B](ma: M[B])(implicit ev: Trivial): Kleisli[M, A, B] = Kleisli.const(ma)
    }
}

private[data] sealed abstract class KleisliInstances0 extends KleisliInstances1 {
  implicit final def kleisliSplit[F[_]](implicit ev: FlatMap[F]): Split[({ type L[x, y] = Kleisli[F, x, y] })#L] =
    new KleisliSplit[F] { final def F: FlatMap[F] = ev }

  implicit final def kleisliStrong[F[_]](implicit ev: Functor[F]): Strong[({ type L[x, y] = Kleisli[F, x, y] })#L] =
    new KleisliStrong[F] { final def F: Functor[F] = ev }

  implicit final def kleisliFlatMap[F[_]: FlatMap, A]: FlatMap[({ type L[x] = Kleisli[F, A, x] })#L] =
    new FlatMap[({ type L[x] = Kleisli[F, A, x] })#L] {
      final def flatMap[B, C](fa: Kleisli[F, A, B])(f: B => Kleisli[F, A, C]): Kleisli[F, A, C] = fa.flatMap(f)
      final def map[B, C](fa: Kleisli[F, A, B])(f: B => C): Kleisli[F, A, C] = fa.map(f)
    }

  implicit final def kleisliSemigroup[F[_], A, B](implicit M: Semigroup[F[B]]): Semigroup[Kleisli[F, A, B]] =
    new KleisliSemigroup[F, A, B] { final def FB: Semigroup[F[B]] = M }

  implicit final def kleisliSemigroupK[F[_]](implicit
    ev: FlatMap[F]
  ): SemigroupK[({ type L[x] = Kleisli[F, x, x] })#L] =
    new KleisliSemigroupK[F] { final def F: FlatMap[F] = ev }
}

private[data] sealed abstract class KleisliInstances1 extends KleisliInstances2 {
  implicit final def kleisliApplicative[F[_], A](implicit
    F: Applicative[F]
  ): Applicative[({ type L[x] = Kleisli[F, A, x] })#L] = new Applicative[({ type L[x] = Kleisli[F, A, x] })#L] {
    final def pure[B](x: B): Kleisli[F, A, B] = Kleisli.pure(x)
    final def ap[B, C](f: Kleisli[F, A, B => C])(fa: Kleisli[F, A, B]): Kleisli[F, A, C] = fa.ap(f)
    override final def map[B, C](fb: Kleisli[F, A, B])(f: B => C): Kleisli[F, A, C] = fb.map(f)
    override final def product[B, C](fb: Kleisli[F, A, B], fc: Kleisli[F, A, C]): Kleisli[F, A, (B, C)] = fb.zip(fc)
  }
}

private[data] sealed abstract class KleisliInstances2 extends KleisliInstances3 {
  implicit final def kleisliApply[F[_], A](implicit F: Apply[F]): Apply[({ type L[x] = Kleisli[F, A, x] })#L] =
    new Apply[({ type L[x] = Kleisli[F, A, x] })#L] {
      final def ap[B, C](f: Kleisli[F, A, B => C])(fa: Kleisli[F, A, B]): Kleisli[F, A, C] = fa.ap(f)
      override final def product[B, C](fb: Kleisli[F, A, B], fc: Kleisli[F, A, C]): Kleisli[F, A, (B, C)] = fb.zip(fc)
      final def map[B, C](fa: Kleisli[F, A, B])(f: B => C): Kleisli[F, A, C] = fa.map(f)
    }
}

private[data] sealed abstract class KleisliInstances3 extends KleisliInstances4 {
  implicit final def kleisliFunctor[F[_]: Functor, A]: Functor[({ type L[x] = Kleisli[F, A, x] })#L] =
    new Functor[({ type L[x] = Kleisli[F, A, x] })#L] {
      final def map[B, C](fa: Kleisli[F, A, B])(f: B => C): Kleisli[F, A, C] = fa.map(f)
    }
}

private[data] sealed abstract class KleisliInstances4 {
  implicit final def kleisliMonadReader[F[_]: Monad, A]: MonadReader[({ type L[x] = Kleisli[F, A, x] })#L, A] =
    new MonadReader[({ type L[x] = Kleisli[F, A, x] })#L, A] {
      final def pure[B](x: B): Kleisli[F, A, B] = Kleisli.pure[F, A, B](x)
      final def flatMap[B, C](fa: Kleisli[F, A, B])(f: B => Kleisli[F, A, C]): Kleisli[F, A, C] = fa.flatMap(f)
      final val ask: Kleisli[F, A, A] = Kleisli.ask[F, A]
      final def local[B](f: A => A)(fa: Kleisli[F, A, B]): Kleisli[F, A, B] = fa.local(f)
    }
}

private trait KleisliArrow[F[_]] extends Arrow[({ type L[x, y] = Kleisli[F, x, y] })#L]
  with KleisliSplit[F] with KleisliStrong[F] {
  implicit def F: Monad[F]

  final def lift[A, B](f: A => B): Kleisli[F, A, B] = new Kleisli[F, A, B] {
    final def run(a: A): F[B] = F.pure(f(a))
  }

  final def id[A]: Kleisli[F, A, A] = Kleisli.ask[F, A]

  final override def second[A, B, C](fa: Kleisli[F, A, B]): Kleisli[F, (C, A), (C, B)] =
    super[KleisliStrong].second(fa)

  final override def split[A, B, C, D](f: Kleisli[F, A, B], g: Kleisli[F, C, D]): Kleisli[F, (A, C), (B, D)] =
    super[KleisliSplit].split(f, g)
}

private trait KleisliSplit[F[_]] extends Split[({ type L[x, y] = Kleisli[F, x, y] })#L] {
  implicit def F: FlatMap[F]

  def split[A, B, C, D](f: Kleisli[F, A, B], g: Kleisli[F, C, D]): Kleisli[F, (A, C), (B, D)] =
    new Kleisli[F, (A, C), (B, D)] {
      final def run(a: (A, C)): F[(B, D)] = F.flatMap(f.run(a._1))(b => F.map(g.run(a._2))(d => (b, d)))
    }

  final def compose[A, B, C](f: Kleisli[F, B, C], g: Kleisli[F, A, B]): Kleisli[F, A, C] = f.compose(g)
}

private trait KleisliStrong[F[_]] extends Strong[({ type L[x, y] = Kleisli[F, x, y] })#L] {
  implicit def F: Functor[F]

  final override def lmap[A, B, C](fab: Kleisli[F, A, B])(f: C => A): Kleisli[F, C, B] = fab.local(f)
  final override def rmap[A, B, C](fab: Kleisli[F, A, B])(f: B => C): Kleisli[F, A, C] = fab.map(f)
  final override def dimap[A, B, C, D](fab: Kleisli[F, A, B])(f: C => A)(g: B => D): Kleisli[F, C, D] = fab.dimap(f)(g)

  def first[A, B, C](fa: Kleisli[F, A, B]): Kleisli[F, (A, C), (B, C)] = fa.first[C]
  def second[A, B, C](fa: Kleisli[F, A, B]): Kleisli[F, (C, A), (C, B)] = fa.second[C]
}

private trait KleisliSemigroup[F[_], A, B] extends Semigroup[Kleisli[F, A, B]] {
  implicit def FB: Semigroup[F[B]]

  final override def combine(fa: Kleisli[F, A, B], fb: Kleisli[F, A, B]): Kleisli[F, A, B] =
    new Kleisli[F, A, B] {
      final def run(a: A): F[B] = FB.combine(fa.run(a), fb.run(a))
    }
}

private trait KleisliMonoid[F[_], A, B] extends Monoid[Kleisli[F, A, B]] with KleisliSemigroup[F, A, B] {
  implicit def FB: Monoid[F[B]]

  final override def empty: Kleisli[F, A, B] = Kleisli.const(FB.empty)
}

private trait KleisliSemigroupK[F[_]] extends SemigroupK[({ type L[x] = Kleisli[F, x, x] })#L] {
  implicit def F: FlatMap[F]

  final override def combineK[A](a: Kleisli[F, A, A], b: Kleisli[F, A, A]): Kleisli[F, A, A] = a.compose(b)
}

private trait KleisliMonoidK[F[_]] extends MonoidK[({ type L[x] = Kleisli[F, x, x] })#L] with KleisliSemigroupK[F] {
  implicit def F: Monad[F]

  final override def empty[A]: Kleisli[F, A, A] = Kleisli.ask[F, A]
}

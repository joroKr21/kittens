package cats.derived

import cats.{Applicative, Apply, NonEmptyTraverse, Traverse}
import shapeless3.deriving.Derived
import shapeless3.deriving.K1.*

import scala.annotation.*
import scala.compiletime.*

@implicitNotFound("""Could not derive NonEmptyTraverse for ${F}.
Make sure that F[_] satisfies one of the following conditions:
  * nested type [x] =>> G[H[x]] where G: NonEmptyTraverse and H: NonEmptyTraverse
  * generic case class where at least one field forms NonEmptyTraverse and the rest form Traverse
  * generic sealed trait where all subclasses form NonEmptyTraverse
  * generic enum where all variants form NonEmptyTraverse""")
type DerivedNonEmptyTraverse[F[_]] = Derived[NonEmptyTraverse[F]]
object DerivedNonEmptyTraverse:
  @nowarn("msg=unused import")
  inline def apply[F[_]]: NonEmptyTraverse[F] =
    import DerivedTraverse.given
    import DerivedNonEmptyTraverse.given
    summonInline[DerivedNonEmptyTraverse[F]].instance

  @nowarn("msg=unused import")
  inline def strict[F[_]]: NonEmptyTraverse[F] =
    import DerivedTraverse.Strict.given
    import DerivedNonEmptyTraverse.Strict.given
    summonInline[DerivedNonEmptyTraverse[F]].instance

  given nested[F[_], G[_]](using
      F: => (NonEmptyTraverse |: Derived)[F],
      G: => (NonEmptyTraverse |: Derived)[G]
  ): DerivedNonEmptyTraverse[F <<< G] =
    new Lazy(() => F.unify.compose(using G.unify)) with NonEmptyTraverse[F <<< G]:
      export delegate.*

  def product[F[_]](ev: NonEmptyTraverse[?])(using
      inst: ProductInstances[Traverse |: Derived, F]
  ): DerivedNonEmptyTraverse[F] =
    Strict.product(ev)(using inst.unify)

  inline given product[F[_]](using gen: ProductGeneric[F]): DerivedNonEmptyTraverse[F] =
    product(summonFirst[NonEmptyTraverse |: Derived, gen.MirroredElemTypes].unify)

  given [F[_]](using => CoproductInstances[NonEmptyTraverse |: Derived, F]): DerivedNonEmptyTraverse[F] =
    Strict.coproduct

  @deprecated("Kept for binary compatibility", "3.2.0")
  protected given [F[_]: NonEmptyTraverse |: Derived, G[_]: NonEmptyTraverse |: Derived]
      : DerivedNonEmptyTraverse[[x] =>> F[G[x]]] = nested

  trait Product[T[x[_]] <: Traverse[x], F[_]](@unused ev: NonEmptyTraverse[?])(using
      @unused inst: ProductInstances[T, F]
  ) extends NonEmptyTraverse[F],
        DerivedReducible.Product[T, F],
        DerivedTraverse.Product[T, F]:

    final override def nonEmptyTraverse[G[_]: Apply, A, B](fa: F[A])(f: A => G[B]): G[F[B]] =
      traverse[Alt[G], A, B](fa)(f.andThen(Left.apply)) match
        case Left(value) => value
        case Right(_) => ???

  trait Coproduct[T[x[_]] <: NonEmptyTraverse[x], F[_]](using inst: CoproductInstances[T, F])
      extends NonEmptyTraverse[F],
        DerivedReducible.Coproduct[T, F],
        DerivedTraverse.Coproduct[T, F]:

    final override def nonEmptyTraverse[G[_], A, B](fa: F[A])(f: A => G[B])(using G: Apply[G]): G[F[B]] =
      inst.fold(fa)([f[a] <: F[a]] => (F: T[f], fa: f[A]) => G.widen[f[B], F[B]](F.nonEmptyTraverse(fa)(f)))

  private type Alt[F[_]] = [A] =>> Either[F[A], A]
  private given [F[_]](using F: Apply[F]): Applicative[Alt[F]] with
    override def pure[A](x: A) = Right(x)
    override def map[A, B](fa: Alt[F][A])(f: A => B) = fa match
      case Left(fa) => Left(F.map(fa)(f))
      case Right(a) => Right(f(a))
    override def ap[A, B](ff: Alt[F][A => B])(fa: Alt[F][A]) = (ff, fa) match
      case (Left(ff), Left(fa)) => Left(F.ap(ff)(fa))
      case (Left(ff), Right(a)) => Left(F.map(ff)(_(a)))
      case (Right(f), Left(fa)) => Left(F.map(fa)(f))
      case (Right(f), Right(a)) => Right(f(a))

  object Strict:
    def product[F[_]: ProductInstancesOf[Traverse]](ev: NonEmptyTraverse[?]): DerivedNonEmptyTraverse[F] =
      new Product[Traverse, F](ev)
        with DerivedReducible.Product[Traverse, F](ev)
        with DerivedTraverse.Product[Traverse, F]
        with DerivedFunctor.Generic[Traverse, F] {}

    inline given product[F[_]](using gen: ProductGeneric[F]): DerivedNonEmptyTraverse[F] =
      product(summonFirst[NonEmptyTraverse, gen.MirroredElemTypes])

    given coproduct[F[_]](using
        inst: => CoproductInstances[NonEmptyTraverse |: Derived, F]
    ): DerivedNonEmptyTraverse[F] =
      given CoproductInstances[NonEmptyTraverse, F] = inst.unify
      new NonEmptyTraverse[F] with Coproduct[NonEmptyTraverse, F] {}

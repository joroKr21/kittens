package cats.derived

import cats.Invariant
import shapeless3.deriving.{Const, Derived}
import shapeless3.deriving.K1.*

import scala.annotation.*
import scala.compiletime.*

@implicitNotFound("""Could not derive Invariant for ${F}.
Make sure it satisfies one of the following conditions:
  * constant type [x] => T
  * nested type [x] => G[H[x]] where G: Invariant and H: Invariant
  * generic case class where all fields form Invariant
  * generic sealed trait where all subclasses form Invariant
  * generic enum where all variants form Invariant""")
type DerivedInvariant[F[_]] = Derived[Invariant[F]]
object DerivedInvariant:
  @nowarn("msg=unused import")
  inline def apply[F[_]]: Invariant[F] =
    import DerivedInvariant.given
    summonInline[DerivedInvariant[F]].instance

  @nowarn("msg=unused import")
  inline def strict[F[_]]: Invariant[F] =
    import Strict.given
    summonInline[DerivedInvariant[F]].instance

  given [T]: DerivedInvariant[Const[T]] = new Invariant[Const[T]]:
    def imap[A, B](fa: T)(f: A => B)(g: B => A): T = fa

  given nested[F[_], G[_]](using
      F: => (Invariant |: Derived)[F],
      G: => (Invariant |: Derived)[G]
  ): DerivedInvariant[F <<< G] =
    new Lazy(() => F.unify.compose(using G.unify)) with Invariant[F <<< G]:
      export delegate.*

  given [F[_]](using inst: => Instances[Invariant |: Derived, F]): DerivedInvariant[F] =
    generic(using inst.unify)

  @deprecated("Kept for binary compatibility", "3.2.0")
  protected given [F[_]: Invariant |: Derived, G[_]: Invariant |: Derived]: DerivedInvariant[[x] =>> F[G[x]]] =
    nested

  private def generic[F[_]: InstancesOf[Invariant]]: DerivedInvariant[F] =
    new Generic[Invariant, F] {}

  trait Generic[T[f[_]] <: Invariant[f], F[_]](using inst: Instances[T, F]) extends Invariant[F]:
    final override def imap[A, B](fa: F[A])(f: A => B)(g: B => A): F[B] =
      inst.map(fa)([f[_]] => (F: T[f], fa: f[A]) => F.imap(fa)(f)(g))

  object Strict:
    given product[F[_]: ProductInstancesOf[Invariant]]: DerivedInvariant[F] = generic
    given coproduct[F[_]](using inst: => CoproductInstances[Invariant |: Derived, F]): DerivedInvariant[F] =
      generic(using inst.unify)

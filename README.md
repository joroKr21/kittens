# Kittens: automatic type class derivation for Cats

**Kittens** is a Scala library which provides instances of type classes from the [Cats][cats] library for arbitrary
algebraic data types (ADTs) using [shapeless][shapeless]-based automatic type class derivation. It also provides utility
functions related to `Applicative` such as `lift`, `traverse` and `sequence` to `HList`, `Record` and case classes.

![kittens image](http://plastic-idolatry.com/erik/kittens2x.png)

Kittens is part of the [Typelevel][typelevel] family of projects. It is an Open Source project under the Apache
License v2, hosted on [GitHub][source]. Binary artifacts will be published to the [Sonatype OSS Repository Hosting
service][sonatype] and synced to Maven Central.

It is available for Scala 2.12 and 2.13, Scala.js 1.5 and Scala Native 0.4.

To get started with sbt, add the following to your `build.sbt` file:

```Scala
libraryDependencies += "org.typelevel" %% "kittens" % "latestVersion" // indicated in the badge below
```

[![Typelevel library](https://img.shields.io/badge/typelevel-library-green.svg)](https://typelevel.org/projects#cats)
[![Build status](https://github.com/typelevel/kittens/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/typelevel/kittens/actions)
[![Gitter channel](https://badges.gitter.im/typelevel/kittens.svg)](https://gitter.im/typelevel/kittens)
[![Scala.js](http://scala-js.org/assets/badges/scalajs-1.5.0.svg)](http://scala-js.org)
[![Latest version](https://img.shields.io/maven-central/v/org.typelevel/kittens_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/org.typelevel/kittens_2.12)

## Scala 2

Instance derivations are available for the following type classes:

* `Eq`, `PartialOrder`, `Order`, `Hash`
* `Show`, pretty `Show`
* `Empty`, `EmptyK` (from Alleycats)
* `Semigroup`, `CommutativeSemigroup`, `SemigroupK`
* `Monoid`, `CommutativeMonoid`, `MonoidK`
* `Functor`, `Contravariant`, `Invariant`
* `Pure` (from Alleycats), `Apply`, `Applicative`
* `Foldable`, `Reducible`
* `Traverse`, `NonEmptyTraverse`
* `ConsK` (from Alleycats)

See the [Type class support matrix](#type-class-support-matrix) for more details.

### Derivation examples

```scala
scala> import cats.implicits._, cats._, cats.derived._

scala> case class Cat[Food](food: Food, foods: List[Food])
defined class Cat

scala> val cat = Cat(1, List(2, 3))
cat: Cat[Int] = Cat(1,List(2, 3))
```

#### Derive `Functor`

```scala
scala> implicit val fc: Functor[Cat] = semiauto.functor
FC: cats.Functor[Cat] = cats.derived.MkFunctor2$$anon$4@1c60573f

scala> cat.map(_ + 1)
res0: Cat[Int] = Cat(2,List(3, 4))
```

#### Derive `Show`

Note that the derived `Show` also prints out field names, so it might be preferable to the default `toString`: 

```scala
scala> case class Address(street: String, city: String, state: String)
scala> case class ContactInfo(phoneNumber: String, address: Address)
scala> case class People(name: String, contactInfo: ContactInfo)

scala> val mike = People("Mike", ContactInfo("202-295-3928", Address("1 Main ST", "Chicago", "IL")))

scala> // existing Show instance for Address
scala> implicit val addressShow: Show[Address] =
         a => s"${a.street}, ${a.city}, ${a.state}"

scala> implicit val peopleShow: Show[People] = semiauto.show // auto derive Show for People

scala> mike.show
res0: String = People(name = Mike, contactInfo = ContactInfo(phoneNumber = 202-295-3928, address = 1 Main ST, Chicago, IL))
```

Note that in this example,
the derivation generated instances for all referenced classes but still respected the existing instance in scope.
For different ways to derive instances, please see [Derivation on Scala 2](#derivation-on-scala-2) below. 

### Sequence examples

Note that to run these examples, you need partial unification enabled.
For **Scala 2.12** you should add the following to your `build.sbt`:

```scala
scalacOptions += "-Ypartial-unification"
```

```scala
scala> import cats.implicits._, cats.sequence._
import cats.implicits._
import cats.sequence._

scala> val f1 = (_: String).length
f1: String => Int = <function1>

scala> val f2 = (_: String).reverse
f2: String => String = <function1>

scala> val f3 = (_: String).toFloat
f3: String => Double = <function1>

scala> val f = sequence(f1, f2, f3)
f: String => shapeless.::[Int,shapeless.::[String,shapeless.::[Float,shapeless.HNil]]] = <function1>

scala> f("42.0")
res0: shapeless.::[Int,shapeless.::[String,shapeless.::[Float,shapeless.HNil]]] = 4 :: 0.24 :: 42.0 :: HNil

//or generic over ADTs
scala>  case class MyCase(a: Int, b: String, c: Float)
defined class MyCase

scala>  val myGen = sequenceGeneric[MyCase]
myGen: cats.sequence.sequenceGen[MyCase] = cats.sequence.SequenceOps$sequenceGen@63ae3243

scala> val f = myGen(a = f1, b = f2, c = f3)
f: String => MyCase = <function1>

scala> f("42.0")
res1: MyCase = MyCase(4,0.24,42.0)
```

Traverse works similarly except you need a `shapeless.Poly`.

### Lift examples

```scala
scala> import cats._, implicits._, lift._
import cats._
import implicits._
import lift._

scala> def foo(x: Int, y: String, z: Float) = s"$x - $y - $z"

scala> val lifted = Applicative[Option].liftA(foo _)
lifted: (Option[Int], Option[String], Option[Float]) => Option[String] = <function3>

scala> lifted(Some(1), Some("a"), Some(3.2f))
res0: Option[String] = Some(1 - a - 3.2)
```

### Derivation on Scala 2

There are three options for type class derivation on Scala 2:
`cats.derived.auto`, `cats.derived.cached` and `cats.derived.semiauto`.
The recommended best practice is to use `semiauto`:

```scala
import cats.derived

implicit val showFoo: Show[Foo] = derived.semiauto.show
```

This will respect all existing instances even if the field is a type constructor.
For example `Show[List[A]]` will use the native `Show` instance for `List` and derived instance for `A`.
And it manually caches the result to `val showFoo`.

#### Auto derivation

```scala
import derived.auto.show._
```

A downside is that it will derive an instance from scratch for every use site, increasing compilation time.

#### Cached derivation

```scala 
import derived.cached.show._
```

Use this one with caution - it caches the derived instance globally.
So it's only applicable if the instance is global in the application.
This could be problematic for libraries, which have no control over the uniqueness of an instance at use site.

#### Semiauto derivation (recommended)

```scala
implicit val showFoo: Show[Foo] = derived.semiauto.show
```

A downside is we need to write one for every type that needs an instance.

## Scala 3

There are five options for type class derivation on Scala 3.
The recommended way is to `import cats.derived.*` and use `derives` clauses.

In contrast to Scala 2:
 - Cached derivation is not supported (and also not necessary)
 - [Type Class Derivation](https://docs.scala-lang.org/scala3/reference/contextual/derivation.html) is supported
 - A `strict` mode is available for `semiauto` and `derives` clauses

#### `derives` clause (recommended)

Kittens supports Scala 3's [derivation syntax](https://docs.scala-lang.org/scala3/reference/contextual/derivation.html). 
Similar to Scala 2, instances will be derived recursively if necessary.

```scala 3
import cats.derived.*

// No instances declared for Name
case class Name(value: String)
case class Person(name: Name, age: Int) derives Eq, Show

enum CList[+A] derives Functor:
  case CNil
  case CCons(head: A, tail: CList[A])
```

Note that the `derives` clause has a fundamental limitation:
it generates an instance that requires the type class for all type parameters, even if not necessary.
The following example shows a rough equivalent of how a `derives Monoid` clause is desugared:

```scala 3
case class Concat[+A](left: Vector[A], right: Vector[A])
object Concat:
  // Note that the `Monoid[A]` requirement is not needed,
  // because `Monoid[Vector[A]]` is defined for any `A`.
  given [A: Monoid]: Monoid[Concat[A]] = Monoid.derived
```

In such cases it is recommended to use semiauto derivation, described below.

#### Semiauto derivation

This looks similar to `semiauto` for Scala 2.
Instances will be derived recursively if necessary.

```scala 3
import cats.derived.semiauto

// No instances declared for Name
case class Name(value: String)
case class Person(name: Name, age: Int)

object Person:
  given Eq[Person] = semiauto.eq
  given Show[Person] = semiauto.show

enum CList[+A]:
  case CNil
  case CCons(head: A, tail: CList[A])
  
object CList:
  given Functor[CList] = semiauto.functor
```

#### Strict `derives` clause

Similar to `derives` above, but instances are **not** derived recursively (except for enums and sealed traits).
Users need to be more explicit about which types implement an instance.

```scala 3
import cats.derived.strict.*

// The instances for Name need to be declared explicitly
case class Name(value: String) derives Eq, Show
case class Person(name: Name, age: Int) derives Eq, Show

// A coproduct type (enum) needs only a top-level declaration
enum CList[+A] derives Functor:
  case CNil
  case CCons(head: A, tail: CList[A])
```

The same limitations apply as with the default `derives` clause.

#### Strict semiauto derivation

Similar to `semiauto` above, but instances are **not** derived recursively (except for enums and sealed traits).
Users need to be more explicit about which types implement an instance.

```scala 3
import cats.derived.strict

case class Name(value: String)
case class Person(name: Name, age: Int)

object Person:
  // The instances for Name need to be declared explicitly
  given Eq[Name] = strict.semiauto.eq
  given Show[Name] = strict.semiauto.show
  given Eq[Person] = strict.semiauto.eq
  given Show[Person] = strict.semiauto.show

enum CList[+A]:
  case CNil
  case CCons(head: A, tail: CList[A])
  
object CList:
  // A coproduct type (enum) needs only a top-level declaration
  given Functor[CList] = semiauto.functor
```

#### Auto derivation

This looks similar to `auto` for Scala 2.

```scala 3
import cats.derived.auto.eq.given
import cats.derived.auto.show.given
import cats.derived.auto.functor.given

case class Name(value: String)
case class Person(name: Name, age: Int)

enum CList[+A]:
  case CNil
  case CCons(head: A, tail: CList[A])
```

### Caveats

#### Nested type constructors

We are [currently](https://github.com/typelevel/kittens/issues/473) unable to
derive instances for nested type constructors, such as `Functor[[x] =>>
List[Set[x]]]`.

#### Stack safety

Our derived instances are not stack-safe.
This is a departure from the behaviour for Scala 2
because we didn't want to incur the performance penalty of trampolining all instances in `cats.Eval`.
If your data-type is recursive or _extremely_ large, then you may want to write instances by hand instead.

#### Missing features

Kittens for Scala 3 is built on top of [Shapeless 3](https://github.com/typelevel/shapeless-3)
which has a completely different API than [Shapeless 2](https://github.com/milessabin/shapeless),
so we don't support features like `Sequence` and `Lift`.

`ConsK` derivation is also not supported, although we expect this to be
[added](https://github.com/typelevel/kittens/issues/489) in a future release.

## Type class support matrix

Legend:
- `∀` - all must satisfy a constraint
- `∃` - at lest one must satisfy a constraint
- `∃!` - exactly one must satisfy a constraint
- `∧` - both constraints must be satisfied  
- `∨` - either constraint must be satisfied

#### For monomorphic types

| Type Class           | Case Classes                   | Sealed Traits            | Singleton types |
|----------------------|--------------------------------|--------------------------|:---------------:|
| CommutativeMonoid    | ∀ fields: CommutativeMonoid    | ✗                        |        ✗        |
| CommutativeSemigroup | ∀ fields: CommutativeSemigroup | ✗                        |        ✗        |
| Empty                | ∀ fields: Empty                | ∃! variant: Empty        |        ✗        |
| Eq                   | ∀ fields: Eq                   | ∀ variants: Eq           |        ✓        |
| Hash                 | ∀ fields: Hash                 | ∀ variants: Hash         |        ✓        |
| Monoid               | ∀ fields: Monoid               | ✗                        |        ✗        |
| Order                | ∀ fields: Order                | ∀ variants: Order        |        ✓        |
| PartialOrder         | ∀ fields: PartialOrder         | ∀ variants: PartialOrder |        ✓        |
| Semigroup            | ∀ fields: Semigroup            | ✗                        |        ✗        |
| Show                 | ∀ fields: Show                 | ∀ variants: Show         |        ✓        |
| ShowPretty           | ∀ fields: ShowPretty           | ∀ variants: ShowPretty   |        ✓        |

#### For polymorphic types

| Type Class          | Case Classes                                   | Sealed Traits                | Constant Types `λ[x => T]` | Nested Types `λ[x => F[G[x]]]`                                            |
|---------------------|------------------------------------------------|------------------------------|----------------------------|---------------------------------------------------------------------------|
| Applicative         | ∀ fields: Applicative                          | ✗                            | for T: Monoid              | for F: Applicative and G: Applicative                                     |
| Apply               | ∀ fields: Apply                                | ✗                            | for T: Semigroup           | for F: Apply and G: Apply                                                 |
| Contravariant       | ∀ fields: Contravariant                        | ∀ variants: Contravariant    | for any T                  | for F: Functor and G: Contravariant                                       |
| EmptyK              | ∀ fields: EmptyK                               | ∃! variant: EmptyK           | for T: Empty               | for F: EmptyK and any G ∨ for F: Pure and G: EmptyK                       |
| Foldable            | ∀ fields: Foldable                             | ∀ variants: Foldable         | for any T                  | for F: Foldable and G: Foldable                                           |
| Functor             | ∀ fields: Functor                              | ∀ variants: Functor          | for any T                  | for F: Functor and G: Functor ∨ for F: Contravariant and G: Contravariant |
| Invariant           | ∀ fields: Invariant                            | ∀ variants: Invariant        | for any T                  | for F: Invariant and G: Invariant                                         |
| MonoidK             | ∀ fields: MonoidK                              | ✗                            | for T: Monoid              | for F: MonoidK and any G ∨ for F: Applicative and G: MonoidK              |
| NonEmptyTraverse    | ∃ field: NonEmptyTraverse ∧ ∀ fields: Traverse | ∀ variants: NonEmptyTraverse | ✗                          | for F: NonEmptyTraverse and G: NonEmptyTraverse                           |
| Pure                | ∀ fields: Pure                                 | ✗                            | for T: Empty               | for F: Pure and G: Pure                                                   |
| Reducible           | ∃ field: Reducible ∧ ∀ fields: Foldable        | ∀ variants: Reducible        | ✗                          | for F: Reducible and G: Reducible                                         |
| SemigroupK          | ∀ fields: SemigroupK                           | ✗                            | for T: Semigroup           | for F: SemigroupK and any G ∨ for F: Apply and G: SemigroupK              |
| Traverse            | ∀ fields: Traverse                             | ∀ variants: Traverse         | for any T                  | for F: Traverse and G: Traverse                                           |
| **Scala 3 only** ↓  |
| NonEmptyAlternative | ∀ fields: NonEmptyAlternative                  | ✗                            | ✗                          | for F: NonEmptyAlternative and G: Applicative                             |
| Alternative         | ∀ fields: Alternative                          | ✗                            | ✗                          | for F: Alternative and G: Applicative                                     |

[cats]: https://github.com/typelevel/cats
[shapeless]: https://github.com/milessabin/shapeless
[typelevel]: http://typelevel.org/
[source]: https://github.com/typelevel/kittens
[sonatype]: https://oss.sonatype.org/

## Participation

The Kittens project supports the [Scala code of conduct][codeofconduct] and wants all of its
channels (mailing list, Gitter, GitHub, etc.) to be welcoming environments for everyone.

[codeofconduct]: https://www.scala-lang.org/conduct/

## Building kittens

Kittens is built with SBT 1.x, and its master branch is built with Scala 2.13 by default.

## Contributors
+ Cody Allen <ceedubs@gmail.com> [@fourierstrick](https://twitter.com/fourierstrick)
+ Georgi Krastev <joro.kr.21@gmail.com> [@Joro_Kr](https://twitter.com/joro_kr)
+ Fabio Labella <fabio.labella2@gmail.com> [@SystemFw](https://twitter.com/labella_fabio)
+ Miles Sabin <miles@milessabin.com> [@milessabin](https://twitter.com/milessabin)
+ Qi Wang [Qi77Qi](http://github.com/Qi77Qi)
+ Kailuo Wang <kailuo.wang@gmail.com> [@kailuowang](https://twitter.com/kailuowang)
+ Tim Spence <timothywspence@gmail.com> [timwspence](https://twitter.com/timwspence)
+ Your name here :-)

package cats.derived

import alleycats.*
import cats.*
import cats.derived.semiauto.*

class MonoidTests { //
  case class Foo(i: Int, b: Option[String]) derives Monoid, Empty, Semigroup
}
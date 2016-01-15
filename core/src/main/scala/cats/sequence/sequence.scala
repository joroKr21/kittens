/*
 * Originally adapted from shapeless-contrib scalaz
 * https://github.com/typelevel/shapeless-contrib/blob/v0.4/scalaz/main/scala/sequence.scala
 *
 */
package cats.sequence

import shapeless._

import cats._
import shapeless.ops.hlist.ZipWithKeys
import shapeless.ops.record.{Values, Keys}

import scala.annotation.implicitNotFound

trait Apply2[FH, OutT] {
  type Out
  def apply(fh: FH, outT: OutT): Out
}

object Apply2 {
  type Aux[FH, OutT, Out0] = Apply2[FH, OutT] { type Out = Out0 }

  implicit def apply2[F[_], H, T <: HList](implicit app: Apply[F]): Aux[F[H], F[T], F[H :: T]] =
    new Apply2[F[H], F[T]] {
      type Out = F[H :: T]
      def apply(fh: F[H], outT: F[T]): Out = app.ap(outT)(app.map(fh)(( (_:H) :: (_:T)).curried))
    }

  implicit def apply2a[F[_, _], A, H, T <: HList](implicit app: Apply[F[A, ?]]): Aux[F[A, H], F[A, T], F[A, H :: T]] =
    apply2[F[A, ?], H, T]
}

@implicitNotFound("cannot construct sequencer, make sure that every item of you hlist ${L} is an Applicative")
trait Sequencer[L <: HList] {
  type Out
  def apply(in: L): Out
}

trait LowPrioritySequencer {
  type Aux[L <: HList, Out0] = Sequencer[L] { type Out = Out0 }

  implicit def consSequencerAux[FH, FT <: HList, OutT]
  (implicit
   st: Aux[FT, OutT],
   ap: Apply2[FH, OutT]
  ): Aux[FH :: FT, ap.Out] =
    new Sequencer[FH :: FT] {
      type Out = ap.Out
      def apply(in: FH :: FT): Out = ap(in.head, st(in.tail))
    }
}

object Sequencer extends LowPrioritySequencer {
  implicit def nilSequencerAux[F[_]: Applicative]: Aux[HNil, F[HNil]] =
    new Sequencer[HNil] {
      type Out = F[HNil]
      def apply(in: HNil): F[HNil] = Applicative[F].pure(HNil: HNil)
    }

  implicit def singleSequencerAux[FH]
  (implicit
   un: Unapply[Functor, FH]
  ): Aux[FH :: HNil, un.M[un.A :: HNil]] =
    new Sequencer[FH :: HNil] {
      type Out = un.M[un.A :: HNil]
      def apply(in: FH :: HNil): Out = un.TC.map(un.subst(in.head)) { _ :: HNil }
    }
}


trait ValueSequencer[L <: HList] {
  type Out
  def apply(in: L): Out
}


object ValueSequencer {
  type Aux[L <: HList, Out0] = ValueSequencer[L] { type Out = Out0 }

  implicit def recordValueAux[L <: HList, V <: HList]
  (implicit
   values:    Values.Aux[L, V],
   sequencer: Sequencer[V]): Aux[L, sequencer.Out] = new ValueSequencer[L] {
    type Out = sequencer.Out
    def apply(in: L): Out = sequencer(values(in))
  }

}


@implicitNotFound("cannot construct sequencer, make sure that every field value of you record ${L} is an Applicative")
trait RecordSequencer[L <: HList] {
  type Out
  def apply(in: L): Out
}


object RecordSequencer {
  type Aux[L <: HList, Out0] = RecordSequencer[L] { type Out = Out0 }

  implicit def recordSequencerAux[L <: HList, VFOut, F[_], K <: HList, VOut <: HList]
  (implicit
   valueSequencer: ValueSequencer.Aux[L, VFOut],
   un:        Unapply.Aux1[Functor, VFOut, F, VOut],
   keys:      Keys.Aux[L, K],
   zip:       ZipWithKeys[K, VOut]
  ): Aux[L, F[zip.Out]] = new RecordSequencer[L] {
    type Out = F[zip.Out]
    def apply(in: L): Out = {
      un.TC.map(un.subst(valueSequencer(in)))(zip(_))
    }
  }

  implicit def recordSequencerAuxRight[L <: HList, VFOut, A,  F[_, _], K <: HList, VOut <: HList]
  (implicit
   valueSequencer: ValueSequencer.Aux[L, VFOut],
   un: Unapply.Aux2Right[Functor, VFOut, F, A, VOut],
   keys:      Keys.Aux[L, K],
   zip:       ZipWithKeys[K, VOut]
  ): Aux[L, F[A, zip.Out]] = recordSequencerAux[L, VFOut, F[A, ?], K, VOut]
}

trait GenericSequencer[L <: HList, T] {
  type Out
  def apply(l: L): Out
}

object GenericSequencer {
  type Aux[L <: HList, T, Out0] = GenericSequencer[L, T] { type Out = Out0 }

  implicit def genericSequencer[L <: HList, T, SOut <: HList, FOut, F[_]]
  (implicit
   rs:              RecordSequencer.Aux[L, FOut],
   gen:             LabelledGeneric.Aux[T, SOut],
   un:              Unapply.Aux1[Functor, FOut, F, SOut]
  ): Aux[L, T, F[T]] = new GenericSequencer[L, T] {
    type Out = F[T]
    def apply(in: L): Out = {
      un.TC.map(un.subst(rs(in)))(gen.from)
    }
  }

  implicit def genericSequencerRight[L <: HList, T, SOut <: HList, FOut, A, F[A, _]]
  (implicit
   rs:              RecordSequencer.Aux[L, FOut],
   gen:             LabelledGeneric.Aux[T, SOut],
   un:              Unapply.Aux2Right[Functor, FOut, F, A, SOut]
  ): Aux[L, T, F[A, T]] = genericSequencer[L, T, SOut, FOut, F[A, ?]]
}



trait SequenceOps {
  implicit class sequenceFunction[L <: HList](self: L) {
    def sequence(implicit seq: Sequencer[L]): seq.Out = seq(self)
    def sequenceRecord(implicit seq: RecordSequencer[L]): seq.Out = seq(self)
  }

  object sequence extends ProductArgs {
    def applyProduct[L <: HList](l: L)(implicit seq: Sequencer[L]): seq.Out = seq(l)
  }

  object sequenceRecord extends RecordArgs {
    def applyRecord[L <: HList](l: L)(implicit seq: RecordSequencer[L]): seq.Out = seq(l)
  }

  class sequenceGen[T] extends RecordArgs {
    def applyRecord[L <: HList](l: L)(implicit seq: GenericSequencer[L, T]): seq.Out = seq(l)
  }

  def sequenceGeneric[T] = new sequenceGen[T]

}

object SequenceOps extends SequenceOps

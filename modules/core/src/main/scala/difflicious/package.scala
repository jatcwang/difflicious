import difflicious.ConfigureOp.{PairBy, SetIgnored}
import izumi.reflect.macrortti.LTag

package object difflicious {

  val setIgnore: SetIgnored = SetIgnored(true)
  val setUnignore: SetIgnored = SetIgnored(false)

  val pairByIndex: PairBy[Any] = PairBy.Index
  def pairBy[A, B](f: A => B)(implicit aTag: LTag[A]): PairBy[A] = PairBy.ByFunc(f, aTag)

}

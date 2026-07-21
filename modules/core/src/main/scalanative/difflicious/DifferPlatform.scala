package difflicious

import difflicious.differ.NumericDiffer
import difflicious.utils.TypeName

trait DifferPlatform {
  implicit val doubleDiffer: NumericDiffer[Double] =
    NumericDiffer.make[Double](_.toString, TypeName[Double]("scala.Double", "Double", Nil))

  implicit val floatDiffer: NumericDiffer[Float] =
    NumericDiffer.make[Float](_.toString, TypeName[Float]("scala.Float", "Float", Nil))
}

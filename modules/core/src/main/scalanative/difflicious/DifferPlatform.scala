package difflicious

import difflicious.differ.NumericDiffer

trait DifferPlatform {
  implicit val doubleDiffer: NumericDiffer[Double] =
    NumericDiffer.make[Double](_.toString)

  implicit val floatDiffer: NumericDiffer[Float] =
    NumericDiffer.make[Float](_.toString)
}

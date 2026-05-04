package difflicious

import org.scalajs.dom
import org.scalajs.dom.intl.NumberFormatOptions
import difflicious.differ.NumericDiffer

trait DifferPlatform {

  // Make the String output match JVM. Otherwise, the number 1 in JVM is "1.0" while in JS it's "1"
  private val doubleFormatter = new dom.intl.NumberFormat(
    locales = "en-US",
    options = new NumberFormatOptions {
      minimumFractionDigits = 1
    },
  )

  implicit val doubleDiffer: NumericDiffer[Double] = {
    NumericDiffer.make[Double](doubleFormatter.format)
  }

  implicit val floatDiffer: NumericDiffer[Float] = {
    NumericDiffer.make[Float](f => doubleFormatter.format(f.toDouble))
  }

}

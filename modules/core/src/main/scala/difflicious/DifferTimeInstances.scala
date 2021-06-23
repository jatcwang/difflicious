package difflicious
import java.time._
import difflicious.differ.EqualsDiffer

trait DifferTimeInstances {

  implicit val dayOfWeekDiffer: EqualsDiffer[DayOfWeek] = {
    Differ.useEquals(_.toString)
  }

  implicit val durationDiffer: EqualsDiffer[Duration] = Differ.useEquals(_.toString)

  implicit val instantDiffer: EqualsDiffer[Instant] = Differ.useEquals(_.toString)

  implicit val localDateDiffer: EqualsDiffer[LocalDate] = Differ.useEquals(_.toString)

  implicit val localDateTimeDiffer: EqualsDiffer[LocalDateTime] = Differ.useEquals(_.toString)

  implicit val localTimeDiffer: EqualsDiffer[LocalTime] = Differ.useEquals(_.toString)

  implicit val monthDiffer: EqualsDiffer[Month] = Differ.useEquals(_.toString)

  implicit val monthDayDiffer: EqualsDiffer[MonthDay] = Differ.useEquals(_.toString)

  implicit val offsetDateTimeDiffer: EqualsDiffer[OffsetDateTime] = Differ.useEquals(_.toString)

  implicit val offsetTimeDiffer: EqualsDiffer[OffsetTime] = Differ.useEquals(_.toString)

  implicit val periodDiffer: EqualsDiffer[Period] = Differ.useEquals(_.toString)

  implicit val yearDiffer: EqualsDiffer[Year] = Differ.useEquals(_.toString)

  implicit val yearMonthDiffer: EqualsDiffer[YearMonth] = Differ.useEquals(_.toString)

  implicit val zonedDateTimeDiffer: EqualsDiffer[ZonedDateTime] = Differ.useEquals(_.toString)

  implicit val zoneIdDiffer: EqualsDiffer[ZoneId] = Differ.useEquals(_.toString)

  implicit val zoneOffsetDiffer: EqualsDiffer[ZoneOffset] = Differ.useEquals(_.toString)

}

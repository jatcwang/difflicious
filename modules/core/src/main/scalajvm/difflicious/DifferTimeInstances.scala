package difflicious
import java.time.*
import difflicious.differ.EqualsDiffer
import difflicious.utils.TypeName

trait DifferTimeInstancesPlatform {

  implicit val dayOfWeekDiffer: EqualsDiffer[DayOfWeek] = useEquals("DayOfWeek")

  implicit val durationDiffer: EqualsDiffer[Duration] = useEquals("Duration")

  implicit val instantDiffer: EqualsDiffer[Instant] = useEquals("Instant")

  implicit val localDateDiffer: EqualsDiffer[LocalDate] = useEquals("LocalDate")

  implicit val localDateTimeDiffer: EqualsDiffer[LocalDateTime] = useEquals("LocalDateTime")

  implicit val localTimeDiffer: EqualsDiffer[LocalTime] = useEquals("LocalTime")

  implicit val monthDiffer: EqualsDiffer[Month] = useEquals("Month")

  implicit val monthDayDiffer: EqualsDiffer[MonthDay] = useEquals("MonthDay")

  implicit val offsetDateTimeDiffer: EqualsDiffer[OffsetDateTime] = useEquals("OffsetDateTime")

  implicit val offsetTimeDiffer: EqualsDiffer[OffsetTime] = useEquals("OffsetTime")

  implicit val periodDiffer: EqualsDiffer[Period] = useEquals("Period")

  implicit val yearDiffer: EqualsDiffer[Year] = useEquals("Year")

  implicit val yearMonthDiffer: EqualsDiffer[YearMonth] = useEquals("YearMonth")

  implicit val zonedDateTimeDiffer: EqualsDiffer[ZonedDateTime] = useEquals("ZonedDateTime")

  implicit val zoneIdDiffer: EqualsDiffer[ZoneId] = useEquals("ZoneId")

  implicit val zoneOffsetDiffer: EqualsDiffer[ZoneOffset] = useEquals("ZoneOffset")

  private def useEquals[T](shortName: String): EqualsDiffer[T] =
    new EqualsDiffer[T](
      isIgnored = false,
      valueToString = _.toString,
      typeName = TypeName[T](s"java.time.$shortName", shortName, Nil),
      canUseEquals = true,
    )

}

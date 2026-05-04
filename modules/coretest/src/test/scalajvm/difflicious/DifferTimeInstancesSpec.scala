package difflicious

import difflicious.testutils._
import munit.ScalaCheckSuite

import java.time._
import org.scalacheck.{Gen, Arbitrary}

class DifferTimeInstancesSpec extends ScalaCheckSuite {

  test("DayOfWeek") {
    implicit val arb: Arbitrary[DayOfWeek] = Arbitrary(Gen.chooseNum(1, 7).map(DayOfWeek.of))
    assertOkIfValuesEqualProp(implicitly[Differ[DayOfWeek]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[DayOfWeek]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[DayOfWeek]])
  }

  test("Duration") {
    implicit val arb: Arbitrary[Duration] = Arbitrary(Gen.posNum[Long].map(Duration.ofNanos))
    assertOkIfValuesEqualProp(implicitly[Differ[Duration]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[Duration]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[Duration]])
  }

  test("Instant") {
    implicit val arb: Arbitrary[Instant] = Arbitrary(Gen.posNum[Long].map(Instant.ofEpochMilli))
    assertOkIfValuesEqualProp(implicitly[Differ[Instant]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[Instant]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[Instant]])
  }

  test("LocalDate") {
    implicit val arb: Arbitrary[LocalDate] = Arbitrary(for {
      year <- Gen.posNum[Int]
      month <- Gen.choose[Int](1, 12)
      days <- Gen.choose[Int](1, 28)
    } yield LocalDate.of(year, month, days))
    assertOkIfValuesEqualProp(implicitly[Differ[LocalDate]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[LocalDate]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[LocalDate]])
  }

  test("LocalDateTime") {
    implicit val arb: Arbitrary[LocalDateTime] = Arbitrary(localDateTimeGen)
    assertOkIfValuesEqualProp(implicitly[Differ[LocalDateTime]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[LocalDateTime]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[LocalDateTime]])
  }

  test("LocalTime") {
    implicit val arb: Arbitrary[LocalTime] = Arbitrary(localTimeGen)
    assertOkIfValuesEqualProp(implicitly[Differ[LocalTime]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[LocalTime]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[LocalTime]])
  }

  test("Month") {
    implicit val arb: Arbitrary[Month] = Arbitrary(Gen.chooseNum(1, 12).map(Month.of))
    assertOkIfValuesEqualProp(implicitly[Differ[Month]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[Month]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[Month]])
  }

  test("MonthDay") {
    implicit val arb: Arbitrary[MonthDay] = Arbitrary(for {
      month <- Gen.choose[Int](1, 12)
      day <- Gen.choose[Int](1, 28)
    } yield MonthDay.of(month, day))
    assertOkIfValuesEqualProp(implicitly[Differ[MonthDay]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[MonthDay]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[MonthDay]])
  }

  test("OffsetDateTime") {
    implicit val arb: Arbitrary[OffsetDateTime] = Arbitrary(for {
      localDateTime <- localDateTimeGen
      zoneOffset <- zoneOffsetGen
    } yield OffsetDateTime.of(localDateTime, zoneOffset))
    assertOkIfValuesEqualProp(implicitly[Differ[OffsetDateTime]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[OffsetDateTime]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[OffsetDateTime]])
  }

  test("OffsetTime") {
    implicit val arb: Arbitrary[OffsetTime] = Arbitrary(for {
      localTime <- localTimeGen
      zoneOffset <- zoneOffsetGen
    } yield OffsetTime.of(localTime, zoneOffset))
    assertOkIfValuesEqualProp(implicitly[Differ[OffsetTime]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[OffsetTime]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[OffsetTime]])
  }

  test("Period") {
    implicit val arb: Arbitrary[Period] = Arbitrary(for {
      years <- Gen.posNum[Int]
      months <- Gen.choose[Int](1, 12)
      days <- Gen.choose[Int](1, 28)
    } yield Period.of(years, months, days))
    assertOkIfValuesEqualProp(implicitly[Differ[Period]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[Period]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[Period]])
  }

  test("Year") {
    implicit val arb: Arbitrary[Year] = Arbitrary(for {
      year <- Gen.posNum[Int]
    } yield Year.of(year))
    assertOkIfValuesEqualProp(implicitly[Differ[Year]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[Year]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[Year]])
  }

  test("YearMonth") {
    implicit val arb: Arbitrary[YearMonth] = Arbitrary(for {
      year <- Gen.posNum[Int]
      month <- Gen.choose(1, 12)
    } yield YearMonth.of(year, month))
    assertOkIfValuesEqualProp(implicitly[Differ[YearMonth]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[YearMonth]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[YearMonth]])
  }

  test("ZonedDateTime") {
    implicit val arb: Arbitrary[ZonedDateTime] = Arbitrary(for {
      localDateTime <- localDateTimeGen
      zoneId <- zoneIdGen
    } yield ZonedDateTime.of(localDateTime, zoneId))
    assertOkIfValuesEqualProp(implicitly[Differ[ZonedDateTime]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[ZonedDateTime]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[ZonedDateTime]])
  }

  test("ZoneId") {
    implicit val arb: Arbitrary[ZoneId] = Arbitrary(zoneIdGen)
    assertOkIfValuesEqualProp(implicitly[Differ[ZoneId]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[ZoneId]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[ZoneId]])
  }

  test("ZoneOffset") {
    implicit val arb: Arbitrary[ZoneOffset] = Arbitrary(zoneOffsetGen)
    assertOkIfValuesEqualProp(implicitly[Differ[ZoneOffset]]) &&
    assertNotOkIfNotEqualProp(implicitly[Differ[ZoneOffset]]) &&
    assertIsOkIfIgnoredProp(implicitly[Differ[ZoneOffset]])
  }

  lazy val zoneOffsetGen: Gen[ZoneOffset] = for {
    hours <- Gen.choose(-12, +12)
  } yield ZoneOffset.ofHours(hours)

  lazy val localTimeGen: Gen[LocalTime] = for {
    hours <- Gen.choose[Int](0, 23)
    minutes <- Gen.choose[Int](0, 59)
    seconds <- Gen.choose[Int](0, 59)
    nanos <- Gen.choose[Int](0, 1000000000 - 1)
  } yield LocalTime.of(hours, minutes, seconds, nanos)

  lazy val localDateTimeGen: Gen[LocalDateTime] = for {
    year <- Gen.posNum[Int]
    month <- Gen.choose[Int](1, 12)
    days <- Gen.choose[Int](1, 28)
    hours <- Gen.choose[Int](0, 23)
    minutes <- Gen.choose[Int](0, 59)
    seconds <- Gen.choose[Int](0, 59)
    nanos <- Gen.choose[Int](0, 1000000000 - 1)
  } yield LocalDateTime.of(year, month, days, hours, minutes, seconds, nanos)

  lazy val zoneIdGen: Gen[ZoneId] = Gen.oneOf(
    Seq(
      "America/Hermosillo",
      "America/Eirunepe",
      "America/St_Vincent",
      "America/Sao_Paulo",
      "Pacific/Tongatapu",
      "Asia/Tokyo",
      "Africa/Cairo",
      "Africa/Abidjan",
      "Africa/Brazzaville",
    ).map(ZoneId.of),
  )
}

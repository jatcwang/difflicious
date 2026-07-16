package difflicious.reporter

import munit.FunSuite

class UlidSpec extends FunSuite {
  test("encodes timestamp and randomness using Crockford base32") {
    val random = Array[Byte](
      0xd3.toByte,
      0x2d.toByte,
      0x99.toByte,
      0x5e.toByte,
      0xd3.toByte,
      0xc8.toByte,
      0x15.toByte,
      0x94.toByte,
      0xd1.toByte,
      0x3f.toByte,
    )

    val ulid = Ulid.encode(1469918176387L, random)

    assertEquals(ulid, "01ARYZ6S43TCPSJQPKS0AS9M9Z")
    assertEquals(Ulid.timestampMillis(ulid), Some(1469918176387L))
  }

  test("decodes canonical ULID timestamp") {
    assertEquals(Ulid.timestampMillis("01ARYZ6S41TSV4RRFFQ69G5FAV"), Some(1469918176385L))
  }

  test("generates valid ULIDs") {
    assert(Ulid.isValid(Ulid.generate()))
  }

  test("zero ULID decodes to epoch") {
    assertEquals(Ulid.Zero, "00000000000000000000000000")
    assertEquals(Ulid.timestampMillis(Ulid.Zero), Some(0L))
  }
}

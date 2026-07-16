package difflicious.reporter

import scala.util.Random

object Ulid {
  val Zero = "00000000000000000000000000"

  private val Length = 26
  private val TimestampLength = 10
  private val RandomLength = 10
  private val Alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
  private val Decoding = {
    val values = Array.fill[Int](128)(-1)
    Alphabet.zipWithIndex.foreach { case (char, index) =>
      values(char.toInt) = index
      values(Character.toLowerCase(char).toInt) = index
    }
    values('i'.toInt) = 1
    values('I'.toInt) = 1
    values('l'.toInt) = 1
    values('L'.toInt) = 1
    values('o'.toInt) = 0
    values('O'.toInt) = 0
    values
  }

  def generate(): String =
    generate(System.currentTimeMillis())

  def generate(timeMillis: Long): String = {
    val random = new Array[Byte](RandomLength)
    Random.nextBytes(random)
    encode(timeMillis, random)
  }

  def encode(timeMillis: Long, random: Array[Byte]): String = {
    require(timeMillis >= 0L && timeMillis <= 0xffffffffffffL, "ULID timestamp must fit in 48 bits")
    require(random.length == RandomLength, "ULID randomness must be 10 bytes")

    val chars = new Array[Char](Length)
    var timestamp = timeMillis
    var timestampIndex = TimestampLength - 1
    while (timestampIndex >= 0) {
      chars(timestampIndex) = Alphabet.charAt((timestamp & 31L).toInt)
      timestamp = timestamp >>> 5
      timestampIndex -= 1
    }

    var outputIndex = TimestampLength
    var randomIndex = 0
    var buffer = 0
    var bits = 0
    while (randomIndex < random.length) {
      buffer = (buffer << 8) | (random(randomIndex) & 0xff)
      bits += 8
      while (bits >= 5) {
        bits -= 5
        chars(outputIndex) = Alphabet.charAt((buffer >>> bits) & 31)
        outputIndex += 1
      }
      randomIndex += 1
    }

    new String(chars)
  }

  def timestampMillis(value: String): Option[Long] =
    if (value.length != Length) None
    else {
      var index = 0
      var timestamp = 0L
      var valid = true
      while (index < Length && valid) {
        decode(value.charAt(index)) match {
          case -1 =>
            valid = false
          case decoded if index < TimestampLength =>
            timestamp = (timestamp << 5) | decoded.toLong
          case _ =>
        }
        index += 1
      }

      if (valid && timestamp <= 0xffffffffffffL) Some(timestamp)
      else None
    }

  def isValid(value: String): Boolean =
    timestampMillis(value).isDefined

  private def decode(char: Char): Int =
    if (char.toInt < Decoding.length) Decoding(char.toInt) else -1
}

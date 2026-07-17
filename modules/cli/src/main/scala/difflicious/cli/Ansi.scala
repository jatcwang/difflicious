package difflicious.cli

object Ansi {
  private val escape = '\u001b'

  def strip(value: String): String = {
    val builder = new StringBuilder(value.length)
    var index = 0

    while (index < value.length) {
      if (value.charAt(index) == escape && index + 1 < value.length && value.charAt(index + 1) == '[') {
        var end = index + 2
        while (end < value.length && value.charAt(end) != 'm') end += 1

        if (end < value.length) index = end + 1
        else {
          builder.append(value.charAt(index))
          index += 1
        }
      } else {
        builder.append(value.charAt(index))
        index += 1
      }
    }

    builder.result()
  }

  def selected(value: String, enabled: Boolean): String =
    if (enabled) s"\u001b[7m$value\u001b[27m" else value

  def red(value: String, enabled: Boolean): String =
    color(value, enabled, 31)

  def purple(value: String, enabled: Boolean): String =
    color(value, enabled, 35)

  def green(value: String, enabled: Boolean): String =
    color(value, enabled, 32)

  def lightGrey(value: String, enabled: Boolean): String =
    color(value, enabled, 37)

  def searchHighlight(value: String, enabled: Boolean): String =
    if (enabled) s"\u001b[43m$value\u001b[49m" else value

  private def color(value: String, enabled: Boolean, code: Int): String =
    if (enabled) s"\u001b[${code}m$value\u001b[39m" else value
}

package testutils

import org.joda.time.{DateTimeZone, DateTimeUtils, DateTime}

trait JodaTimeUtils {
  def withSystemTime[T](dateTime: DateTime)(block: => T): T = {
    DateTimeUtils.setCurrentMillisFixed(
      dateTime.withZone(DateTimeZone.UTC).getMillis
    )
    val result = block
    DateTimeUtils.setCurrentMillisSystem()
    result
  }

  def withSystemTime[T](
      hour: Int,
      minute: Int,
      seconds: Int = 0,
      tz: DateTimeZone = DateTimeZone.UTC
  )(block: => T): T = {
    val time = DateTime.now(tz).withTime(hour, minute, seconds, 0)
    withSystemTime(time)(block)
  }

  def withSystemDateTime[T](
      year: Int,
      month: Int,
      day: Int,
      hour: Int = 0,
      minute: Int = 0,
      seconds: Int = 0,
      tz: DateTimeZone = DateTimeZone.UTC
  )(block: => T): T = {
    val time = new DateTime(year, month, day, hour, minute, seconds, tz)
    withSystemTime(time)(block)
  }
}

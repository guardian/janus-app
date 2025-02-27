package testutils

import java.time.{Clock, Instant, ZoneId, ZonedDateTime}

trait TimeUtils {
  def withSystemTime[T](instant: Instant)(block: Clock => T): T = {
    val fixedClock = Clock.fixed(instant, ZoneId.of("UTC"))
    block(fixedClock)
  }

  def withSystemTime[T](
      hour: Int,
      minute: Int
  )(block: Clock => T): T = {
    val time = ZonedDateTime
      .now(ZoneId.of("UTC"))
      .withHour(hour)
      .withMinute(minute)
      .toInstant
    withSystemTime(time)(block)
  }
}

package testutils

import java.time.{Clock, Instant, ZoneId, ZonedDateTime}

trait TimeUtils {
  def withSystemTime[T](instant: Instant)(block: Clock => T): T = {
    val fixedClock = Clock.fixed(instant, ZoneId.of("UTC"))
    block(fixedClock)
  }

  def withSystemTime[T](
      hour: Int,
      minute: Int,
      zoneId: ZoneId = ZoneId.of("UTC")
  )(block: Clock => T): T = {
    val time =
      ZonedDateTime.now(zoneId).withHour(hour).withMinute(minute).toInstant
    withSystemTime(time)(block)
  }
}

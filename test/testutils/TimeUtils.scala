package testutils

import java.time.{Clock, ZoneId, ZonedDateTime}

trait TimeUtils {
  def withSystemTime[T](hour: Int, minute: Int, zoneId: Option[ZoneId] = None)(
      block: Option[Clock] => T
  ): T = {
    val clock = zoneId.map { zone =>
      val instant =
        ZonedDateTime.now(zone).withHour(hour).withMinute(minute).toInstant
      Clock.fixed(instant, zone)
    }
    block(clock)
  }
}

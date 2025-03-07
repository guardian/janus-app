package testutils

import java.time.{ZoneId, ZonedDateTime}

trait TimeUtils {
  def withSystemTime[T](hour: Int, minute: Int, zoneId: Option[ZoneId] = None)(
      block: Option[ZonedDateTime] => T
  ): T = {
    val time = zoneId.map { zone =>
      ZonedDateTime.now(zone).withHour(hour).withMinute(minute)
    }
    block(time)
  }
}

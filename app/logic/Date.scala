package logic

import models.{DisplayMode, Festive, Normal, Spooky}

import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Date {
  private val simpleDateFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
  private val dateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(ZoneOffset.UTC)
  private val timeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss z").withZone(ZoneOffset.UTC)
  private val friendlyDateFormatter =
    DateTimeFormatter.ofPattern("d MMMM, yyyy").withZone(ZoneOffset.UTC)

  def formatDateTime(date: Instant): String =
    dateTimeFormatter.format(date)

  def formatTime(instant: Instant): String =
    timeFormatter.format(instant)

  def formatDate(instant: Instant): String = {
    friendlyDateFormatter.format(instant)
  }

  def rawDate(instant: Instant): String = {
    simpleDateFormatter.format(instant)
  }

  def isoDateString(instant: Instant): String = {
    DateTimeFormatter.ISO_DATE_TIME.format(instant)
  }

  def formatInterval(
      instant: Instant,
      comparison: Instant = Instant.now()
  ): String = {
    formatDuration(Duration.between(comparison, instant))
  }

  def formatDuration(duration: Duration): String = {
    val hours = duration.toHours
    val minutes = duration.minusHours(hours).toMinutes
    val seconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds

    val hourStr = if (hours == 1) "hour" else "hours"
    val minStr = if (minutes == 1) "minute" else "minutes"
    val secStr = if (seconds == 1) "second" else "seconds"

    s"$hours $hourStr, $minutes $minStr, $seconds $secStr"
  }

  def firstDayOfWeek(instant: Instant): Instant = {
    instant
      .atZone(ZoneOffset.UTC)
      .`with`(DayOfWeek.MONDAY)
      .toInstant
  }

  def weekAround(instant: Instant): (Instant, Instant) = {
    val start = firstDayOfWeek(instant)
    (start, start.plus(7, ChronoUnit.DAYS))
  }

  private[logic] def isInAuditRange(instant: Instant): Boolean = {
    val auditStart = LocalDateTime
      .of(2015, 11, 1, 23, 59, 59)
      .toInstant(ZoneOffset.UTC)
    instant.isAfter(auditStart) &&
    instant.isBefore(Instant.now())
  }

  def prevNextAuditWeeks(
      instant: Instant
  ): (Option[Instant], Option[Instant]) = {
    val week = firstDayOfWeek(instant)
    (
      Some(week.minus(7, ChronoUnit.DAYS)).filter(isInAuditRange),
      Some(week.plus(7, ChronoUnit.DAYS)).filter(isInAuditRange)
    )
  }

  def parseDateStr(dateStr: String): Option[Instant] = {
    try {
      Some {
        LocalDate
          .parse(dateStr, simpleDateFormatter)
          .atStartOfDay()
          .toInstant(ZoneOffset.UTC)
      }
    } catch {
      case _: Exception => None
    }
  }

  def today: Instant = {
    LocalDate
      .now(ZoneOffset.UTC)
      .atStartOfDay()
      .toInstant(ZoneOffset.UTC)
  }

  def maxDuration(d1: Duration, d2: Duration): Duration = {
    if (d1.compareTo(d2) > 0) d1 else d2
  }

  def minDuration(d1: Duration, d2: Duration): Duration = {
    if (d1.compareTo(d2) < 0) d1 else d2
  }

  def displayMode(today: Instant): DisplayMode = {
    val date = today.atZone(ZoneOffset.UTC)
    if (date.getDayOfMonth == 31 && date.getMonthValue == 10) Spooky
    else if (
      (20 to 26).contains(date.getDayOfMonth) && date.getMonthValue == 12
    ) Festive
    else Normal
  }
}

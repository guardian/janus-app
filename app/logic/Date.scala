package logic

import models.{DisplayMode, Festive, Normal, Spooky}

import java.time._
import java.time.format.DateTimeFormatter
import scala.util.Try

object Date {
  private val simpleDateFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
  private val dateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(ZoneOffset.UTC)
  private val timeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss z").withZone(ZoneOffset.UTC)
  private val friendlyDateFormatter =
    DateTimeFormatter.ofPattern("d MMMM, yyyy").withZone(ZoneOffset.UTC)

  def formatDateTime(instant: Instant): String =
    dateTimeFormatter.format(instant)

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
    val minutes = duration.toMinutesPart
    val seconds = duration.toSecondsPart

    val parts = Seq(
      if (hours > 0) Some(s"$hours ${if (hours == 1) "hour" else "hours"}")
      else None,
      if (minutes > 0)
        Some(s"$minutes ${if (minutes == 1) "minute" else "minutes"}")
      else None,
      if (seconds > 0 || (hours == 0 && minutes == 0))
        Some(s"$seconds ${if (seconds == 1) "second" else "seconds"}")
      else None
    ).flatten

    parts.mkString(", ")
  }

  def firstDayOfWeek(instant: Instant): Instant = {
    instant
      .atZone(ZoneOffset.UTC)
      .`with`(DayOfWeek.MONDAY)
      .toInstant
  }

  def weekAround(instant: Instant): (Instant, Instant) = {
    val start = firstDayOfWeek(instant)
    (start, start.plus(Period.ofWeeks(1)))
  }

  private[logic] def isInAuditRange(instant: Instant): Boolean = {
    val auditStart =
      ZonedDateTime.of(2015, 11, 1, 23, 59, 59, 0, ZoneOffset.UTC).toInstant
    instant.isAfter(auditStart) &&
    instant.isBefore(Instant.now())
  }

  def prevNextAuditWeeks(
      instant: Instant
  ): (Option[Instant], Option[Instant]) = {
    val week = firstDayOfWeek(instant)
    (
      Some(week.minus(Period.ofDays(7))).filter(isInAuditRange),
      Some(week.plus(Period.ofDays(7))).filter(isInAuditRange)
    )
  }

  /** Parses a date string in the format "yyyy-MM-dd" and if it's valid returns
    * the instant at the start of that date in UTC.
    */
  def parseDateStr(dateStr: String): Option[Instant] =
    Try {
      val localDate = LocalDate.parse(dateStr, simpleDateFormatter)
      // Convert LocalDate to Instant by setting time to start of day (midnight) in UTC
      localDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    }.toOption

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

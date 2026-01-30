package logic

import models.DisplayMode
import models.DisplayMode.*

import java.time.ZoneOffset.UTC
import java.time.*
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.jdk.DurationConverters.*

object Date {
  private val simpleDateFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(UTC)
  private val dateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(UTC)
  private val timeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(UTC)
  private val friendlyDateFormatter =
    DateTimeFormatter.ofPattern("d MMMM, yyyy").withZone(UTC)

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
    DateTimeFormatter.ISO_INSTANT.format(instant)
  }

  def formatInterval(
      instant: Instant,
      comparison: Instant = Instant.now()
  ): String = {
    formatDuration(Duration.between(comparison, instant))
  }

  def formatDuration(duration: Duration): String = {
    Seq(
      duration.toHours.toInt -> "hour",
      duration.toMinutesPart -> "minute",
      duration.toSecondsPart -> "second"
    ).collect {
      case (1, unit)                  => s"1 $unit"
      case (value, unit) if value > 1 => s"$value ${unit}s"
    }.mkString(", ")
  }

  def formatDuration(fDuration: FiniteDuration): String =
    formatDuration(fDuration.toJava)

  def firstDayOfWeek(instant: Instant): Instant =
    instant
      .atZone(UTC)
      .`with`(DayOfWeek.MONDAY)
      .toInstant

  def weekAround(instant: Instant): (Instant, Instant) = {
    val start = firstDayOfWeek(instant)
    (start, start.plus(Duration.ofDays(7)))
  }

  private[logic] def isInAuditRange(instant: Instant): Boolean = {
    val auditStart =
      ZonedDateTime.of(2015, 11, 1, 23, 59, 59, 0, UTC).toInstant
    instant.isAfter(auditStart) &&
    instant.isBefore(Instant.now())
  }

  def prevNextAuditWeeks(
      instant: Instant
  ): (Option[Instant], Option[Instant]) = {
    val week = firstDayOfWeek(instant)
    (
      Some(week.minus(Duration.ofDays(7))).filter(isInAuditRange),
      Some(week.plus(Duration.ofDays(7))).filter(isInAuditRange)
    )
  }

  /** Parses a date string in the format "yyyy-MM-dd" and if it's valid returns
    * the instant at the start of that date in UTC.
    */
  def parseUtcDateStr(dateStr: String): Option[Instant] = {
    Try {
      val localDate = LocalDate.parse(dateStr, simpleDateFormatter)
      localDate.atStartOfDay(UTC).toInstant
    }.toOption
  }

  def todayUtc: Instant = {
    LocalDate
      .now(UTC)
      .atStartOfDay(UTC)
      .toInstant
  }

  def maxDuration(d1: Duration, d2: Duration): Duration = {
    if (d1.compareTo(d2) > 0) d1 else d2
  }

  def minDuration(d1: Duration, d2: Duration): Duration = {
    if (d1.compareTo(d2) < 0) d1 else d2
  }

  def displayMode(today: ZonedDateTime): DisplayMode = {
    if (today.getDayOfMonth == 31 && today.getMonthValue == 10) Spooky
    else if (
      (20 to 26).contains(today.getDayOfMonth) && today.getMonthValue == 12
    ) Festive
    else Normal
  }
}

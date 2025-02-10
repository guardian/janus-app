package logic

import models.{DisplayMode, Festive, Normal, Spooky}
import org.joda.time.{
  DateTime,
  DateTimeConstants,
  DateTimeZone,
  Duration,
  Interval,
  Period
}
import org.joda.time.format.{
  DateTimeFormat,
  ISODateTimeFormat,
  PeriodFormatterBuilder
}

object Date {
  private val simpleDateFormatter =
    DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC()
  private val dateTimeFormatter =
    DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss").withZoneUTC()
  private val timeFormatter =
    DateTimeFormat.forPattern("HH:mm:ss z").withZoneUTC()
  private val friendlyDateFormatter =
    DateTimeFormat.forPattern("d MMMM, yyyy").withZoneUTC()

  private def toJodaDateTime(instant: java.time.Instant): DateTime = {
    new DateTime(instant.toEpochMilli, DateTimeZone.UTC)
  }

  def formatDateTime(date: DateTime): String =
    dateTimeFormatter.print(date)

  def formatTime(instant: java.time.Instant): String =
    timeFormatter.print(toJodaDateTime(instant))

  def formatDate(date: DateTime): String = {
    friendlyDateFormatter.print(date)
  }

  def rawDate(date: DateTime): String = {
    simpleDateFormatter.print(date)
  }

  def isoDateString(instant: java.time.Instant): String = {
    ISODateTimeFormat.dateTime().print(toJodaDateTime(instant))
  }

  def isoDateString(date: DateTime): String = {
    ISODateTimeFormat.dateTime().print(date)
  }

  def formatInterval(
      instant: java.time.Instant,
      comparison: DateTime = DateTime.now
  ): String = {
    val date = toJodaDateTime(instant)
    formatPeriod(new Interval(comparison, date).toPeriod)
  }

  def formatDuration(duration: Duration): String =
    formatPeriod(duration.toPeriod)

  def formatPeriod(period: Period): String = {
    val minutesAndSeconds = new PeriodFormatterBuilder()
      .appendHours()
      .appendSuffix(" hour", " hours")
      .appendSeparator(", ")
      .appendMinutes()
      .appendSuffix(" minute", " minutes")
      .appendSeparator(", ")
      .appendSeconds()
      .appendSuffix(" second", " seconds")
      .toFormatter
    minutesAndSeconds.print(period)
  }

  def firstDayOfWeek(date: DateTime): DateTime = {
    date.withDayOfWeek(DateTimeConstants.MONDAY)
  }

  def weekAround(date: DateTime): (DateTime, DateTime) = {
    val start = firstDayOfWeek(date)
    (start, start.plusDays(7))
  }

  private[logic] def isInAuditRange(date: DateTime): Boolean = {
    date.isAfter(new DateTime(2015, 11, 1, 23, 59, 59, DateTimeZone.UTC)) &&
    date.isBefore(DateTime.now(DateTimeZone.UTC))
  }

  def prevNextAuditWeeks(
      date: DateTime
  ): (Option[DateTime], Option[DateTime]) = {
    val week = firstDayOfWeek(date)
    (
      Some(week.minusDays(7)).filter(isInAuditRange),
      Some(week.plusDays(7)).filter(isInAuditRange)
    )
  }

  def parseDateStr(dateStr: String): Option[DateTime] = {
    try {
      Some {
        simpleDateFormatter.parseDateTime(dateStr)
      }
    } catch {
      case _: IllegalArgumentException => None
    }
  }

  def today: DateTime = {
    DateTime.now(DateTimeZone.UTC).withTimeAtStartOfDay
  }

  def maxDuration(d1: Duration, d2: Duration): Duration = {
    if (d1.getMillis > d2.getMillis) d1 else d2
  }

  def minDuration(d1: Duration, d2: Duration): Duration = {
    if (d1.getMillis < d2.getMillis) d1 else d2
  }

  def displayMode(today: DateTime): DisplayMode = {
    if (today.dayOfMonth().get == 31 && today.monthOfYear().get == 10) Spooky
    else if (
      (20 to 26)
        .contains(today.dayOfMonth().get) && today.monthOfYear().get == 12
    ) Festive
    else Normal
  }
}

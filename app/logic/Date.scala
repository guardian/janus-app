package logic

import models.{DisplayMode, Festive, Normal, Spooky, WorldCup}
import org.joda.time._
import org.joda.time.format.{
  DateTimeFormat,
  ISODateTimeFormat,
  PeriodFormatterBuilder
}

object Date {
  val simpleDateFormatter =
    DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC()
  val dateTimeFormatter =
    DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss").withZoneUTC()
  val timeFormatter = DateTimeFormat.forPattern("HH:mm:ss z").withZoneUTC()
  val friendlyDateFormatter =
    DateTimeFormat.forPattern("d MMMM, yyyy").withZoneUTC()

  def formatDateTime(date: DateTime): String =
    dateTimeFormatter.print(date)

  def formatTime(date: DateTime): String =
    timeFormatter.print(date)

  def formatDate(date: DateTime): String = {
    friendlyDateFormatter.print(date)
  }

  def rawDate(date: DateTime): String = {
    simpleDateFormatter.print(date)
  }

  def isoDateString(date: DateTime): String = {
    ISODateTimeFormat.dateTime().print(date)
  }

  def formatInterval(
      date: DateTime,
      comparison: DateTime = DateTime.now
  ): String =
    formatPeriod(new Interval(comparison, date).toPeriod)

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
      case e: IllegalArgumentException => None
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
    val day = today.dayOfMonth().get
    val month = today.monthOfYear().get
    val year = today.year().get

    if (day < 22 && month == 8 && year == 2023) WorldCup
    else if (today.dayOfMonth().get == 31 && today.monthOfYear().get == 10)
      Spooky
    else if (
      (20 to 26)
        .contains(today.dayOfMonth().get) && today.monthOfYear().get == 12
    ) Festive
    else Normal
  }
}

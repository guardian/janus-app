package logic

import models.DisplayMode
import models.DisplayMode.*
import play.api.mvc.RequestHeader

import java.time.{Duration, ZoneId, ZoneOffset}
import scala.util.Try

object Customisation {

  /** Extracts requested duration and user's TZ offset from request */
  def durationParams(
      request: RequestHeader
  ): (Option[Duration], Option[ZoneId]) = {
    val duration = Try {
      request.getQueryString("duration").map(ms => Duration.ofMillis(ms.toLong))
    }.toOption.flatten

    val tzOffset = Try {
      request
        .getQueryString("tzOffset")
        .map(hrs => ZoneOffset.ofHours(hrs.toInt))
    }.toOption.flatten

    (duration, tzOffset)
  }

  def displayColour(displayMode: DisplayMode): String = {
    displayMode match {
      case Normal  => "cyan"
      case Spooky  => "purple"
      case Festive => "red"
    }
  }
}

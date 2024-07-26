package logic

import models.{DisplayMode, Festive, Normal, Spooky}
import org.joda.time.{DateTimeZone, Duration}
import play.api.mvc.{Cookies, RequestHeader}

import scala.util.Try

object Customisation {

  /** Extracts requested duration and user's TZ offset from request
    */
  def durationParams(
      request: RequestHeader
  ): (Option[Duration], Option[DateTimeZone]) = {
    val duration = Try {
      request.getQueryString("duration").map(ms => new Duration(ms.toInt))
    }.toOption.flatten
    val tzOffset = Try {
      request
        .getQueryString("tzOffset")
        .map(hrs => DateTimeZone.forOffsetHours(hrs.toInt))
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

  /**
   * The auto-logout functionality is controlled by a UI toggle that sets a Cookie.
   *
   * This function extracts the preference from the cookie for use on the server.
   */
  def autoLogoutPreference(cookies: Cookies): Boolean = {
    cookies.get("janus_auto_logout").exists(_.value == "1")
  }
}

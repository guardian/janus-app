package logic

import java.util.Base64

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Cookie

object Favourites extends Logging {
  def fromCookie(cookie: Cookie): List[String] = {
    try {
      Json
        .parse(Base64.getDecoder.decode(cookie.value))
        .validate[List[String]]
        .fold(
          { a =>
            logger.warn("Could not parse favourites cookie")
            Nil
          },
          identity
        )
    } catch {
      case e @ (_: JsonMappingException | _: JsonParseException |
          _: IllegalArgumentException) =>
        logger.warn(
          s"Failed to parse favourites cookie value: ${cookie.value}",
          e
        )
        Nil
    }
  }

  def fromCookie(cookieOpt: Option[Cookie]): List[String] = {
    cookieOpt.map(fromCookie).getOrElse(Nil)
  }

  def toCookie(favourites: List[String]): Cookie = {
    val value = Base64.getEncoder.encodeToString(
      Json.stringify(Json.toJson(favourites)).getBytes("UTF-8")
    )
    Cookie(
      "favourites",
      value,
      maxAge = Some(86400 * 365),
      path = "/",
      secure = true,
      httpOnly = true
    )
  }

  /** Adds favourite if it does not exist, removes if it does
    */
  def toggleFavourite(
      account: String,
      favourites: List[String]
  ): List[String] = {
    if (favourites.contains(account)) favourites.filterNot(_ == account)
    else favourites :+ account
  }
}

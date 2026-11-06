package controllers

import models.JanusException.throwableWrites
import models.{JanusException, PasskeyEncodings}
import play.api.Logging
import play.api.http.MimeTypes
import play.api.libs.json.Json.toJson
import play.api.mvc.*
import play.twirl.api.Html

import scala.util.{Failure, Success, Try}

/** Provides utilities for handling API responses and redirects in a
  * standardized way. It transforms Try-based computation results into
  * appropriate Play HTTP results.
  */
private[controllers] trait ResultHandler extends Results with Logging {

  def apiResponse[A](action: => Try[A]): Result =
    action match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        Status(err.httpCode)(toJson(err))
      case Failure(err) =>
        logger.error(err.getMessage, err)
        InternalServerError(toJson(err))
      case Success(result: Result) => result
      case Success(html: Html)     => Ok(html)
      case Success(a)              =>
        val json = PasskeyEncodings.mapper.writeValueAsString(a)
        Ok(json).as(MimeTypes.JSON)
    }
}

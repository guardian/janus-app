package models

import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR}

sealed trait AccountConfigStatus
case class FederationConfigError(causedBy: Throwable)
    extends AccountConfigStatus
case object ConfigSuccess extends AccountConfigStatus
case class ConfigWarn(accounts: Set[String]) extends AccountConfigStatus
case class ConfigError(accounts: Set[String]) extends AccountConfigStatus

sealed trait DisplayMode
object Normal extends DisplayMode
object Spooky extends DisplayMode
object Festive extends DisplayMode

case class JanusException(
    userMessage: String,
    engineerMessage: String,
    httpCode: Int,
    causedBy: Option[Throwable]
) extends Exception(engineerMessage, causedBy.orNull)
